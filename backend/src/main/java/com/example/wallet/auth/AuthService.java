package com.example.wallet.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.audit.AuditService;
import com.example.wallet.auth.AuthDtos.LoginRequest;
import com.example.wallet.auth.AuthDtos.RegisterRequest;
import com.example.wallet.auth.AuthDtos.TokenResponse;
import com.example.wallet.common.UuidV7;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.user.Role;
import com.example.wallet.user.User;
import com.example.wallet.user.UserRepository;
import com.example.wallet.user.UserResponse;

/**
 * Registration, login, refresh-token rotation with reuse detection, logout.
 *
 * <p>Rotation: every refresh revokes the presented token and issues a new one in the same
 * family. A legitimate client therefore only ever holds the newest token. If a revoked token
 * is presented again, someone else has a copy (stolen token, or a stolen-then-used one).
 * We can't tell who is the attacker, so the whole family is revoked and both parties must
 * log in again.
 */
@Service
public class AuthService {

    private static final String LOGIN_FAILED = "Invalid email or password";
    private static final String REFRESH_FAILED = "Invalid or expired refresh token";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditService audit;
    private final AuthProperties props;
    private final Clock clock;

    /** Verified against when the email is unknown, so both paths cost one Argon2 run. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       TokenService tokenService, AuditService audit, AuthProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(TokenService.newRefreshTokenValue());
    }

    @Transactional
    public UserResponse register(RegisterRequest request, ClientInfo client) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
        }
        // Role is hard-coded: self-registration can only ever create a CUSTOMER.
        User user = new User(UuidV7.generate(clock), email, passwordEncoder.encode(request.password()),
                Role.CUSTOMER, clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations for the same email raced past existsByEmail; the UNIQUE index decides.
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
        }
        audit.record(user.getId(), "USER_REGISTERED", "user", user.getId(), null, client.ip());
        return UserResponse.from(user);
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public TokenResponse login(LoginRequest request, ClientInfo client) {
        String email = normalizeEmail(request.email());
        Optional<User> found = users.findByEmail(email);

        // Exactly one Argon2 verification on every path: response time must not reveal
        // whether the email exists.
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), found.map(User::getPasswordHash).orElse(dummyPasswordHash));

        String failure = found.isEmpty() ? "UNKNOWN_EMAIL"
                : !passwordMatches ? "BAD_PASSWORD"
                : !found.get().isActive() ? "USER_FROZEN"
                : null;
        if (failure != null) {
            UUID userId = found.map(User::getId).orElse(null);
            // The reason is recorded for investigators; the client always gets the same message.
            audit.record(userId, "LOGIN_FAILED", "user", userId, Map.of("reason", failure, "email", email), client.ip());
            throw new AuthenticationFailedException(LOGIN_FAILED);
        }

        User user = found.get();
        String refreshToken = issueRefreshToken(user.getId(), UuidV7.generate(clock), client).value();
        audit.record(user.getId(), "LOGIN_SUCCEEDED", "user", user.getId(), null, client.ip());
        return tokenResponse(user, refreshToken);
    }

    /** Not rolled back on failure: on reuse, the family revocation must stick. */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public TokenResponse refresh(String presentedToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHashForUpdate(TokenService.hash(presentedToken))
                .orElseThrow(() -> new AuthenticationFailedException(REFRESH_FAILED));

        if (current.isRevoked()) {
            int revoked = refreshTokens.revokeFamily(current.getFamilyId(), now);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("presentedTokenId", current.getId());
            details.put("tokensRevoked", revoked);
            audit.record(current.getUserId(), "REFRESH_TOKEN_REUSE_DETECTED", "refresh_token_family",
                    current.getFamilyId(), details, client.ip());
            throw new AuthenticationFailedException(REFRESH_FAILED);
        }
        if (current.isExpired(now)) {
            throw new AuthenticationFailedException(REFRESH_FAILED);
        }

        User user = users.findById(current.getUserId()).filter(User::isActive).orElse(null);
        if (user == null) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new AuthenticationFailedException(REFRESH_FAILED);
        }

        IssuedRefreshToken next = issueRefreshToken(user.getId(), current.getFamilyId(), client);
        current.rotatedTo(next.id(), now);
        return tokenResponse(user, next.value());
    }

    /** Always succeeds from the client's view: an unknown token reveals nothing. */
    @Transactional
    public void logout(String presentedToken, ClientInfo client) {
        refreshTokens.findByTokenHash(TokenService.hash(presentedToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), clock.instant());
            audit.record(token.getUserId(), "LOGOUT", "refresh_token_family", token.getFamilyId(), null, client.ip());
        });
    }

    @Transactional
    public void logoutAll(AuthUser caller, ClientInfo client) {
        int revoked = refreshTokens.revokeAllForUser(caller.id(), clock.instant());
        audit.record(caller.id(), "LOGOUT_ALL", "user", caller.id(), Map.of("tokensRevoked", revoked), client.ip());
    }

    // ---- helpers -------------------------------------------------------------------------

    private record IssuedRefreshToken(UUID id, String value) {
    }

    private IssuedRefreshToken issueRefreshToken(UUID userId, UUID familyId, ClientInfo client) {
        Instant now = clock.instant();
        String value = TokenService.newRefreshTokenValue();
        RefreshToken token = new RefreshToken(UuidV7.generate(clock), userId, TokenService.hash(value), familyId,
                now, now.plus(props.refreshTokenTtl()), client);
        refreshTokens.save(token);
        return new IssuedRefreshToken(token.getId(), value);
    }

    private TokenResponse tokenResponse(User user, String refreshToken) {
        return new TokenResponse(tokenService.issueAccessToken(user), refreshToken,
                tokenService.accessTokenTtlSeconds(), UserResponse.from(user));
    }

    /**
     * Lower-case in Java, not only via citext: a JDBC parameter arrives as varchar, and
     * Postgres may then compare citext = varchar as text, i.e. case-sensitively.
     */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
