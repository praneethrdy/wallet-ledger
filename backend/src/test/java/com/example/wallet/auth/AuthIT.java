package com.example.wallet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.wallet.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Spec §9 test 10 (auth), plus registration and login hardening. */
@AutoConfigureMockMvc
class AuthIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    AuthProperties authProperties;

    // ---- register ------------------------------------------------------------------------

    @Test
    void registerCreatesCustomerWithArgon2idHash() throws Exception {
        register("Alice@Example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(PASSWORD);
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        register("bob@example.com", PASSWORD).andExpect(status().isCreated());

        register("BOB@example.com", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidatesEmailAndPasswordLength() throws Exception {
        postJson("/api/v1/auth/register", Map.of("email", "not-an-email", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void registerCannotChooseItsOwnRole() throws Exception {
        postJson("/api/v1/auth/register", Map.of("email", "eve@example.com", "password", PASSWORD, "role", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("role"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
    }

    // ---- login ---------------------------------------------------------------------------

    @Test
    void loginReturnsTokensAndUser() throws Exception {
        register("carol@example.com", PASSWORD);

        login("CAROL@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("carol@example.com"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void accessTokenHasRequiredClaimsAnd15MinuteLifetime() throws Exception {
        String userId = registerId("dave@example.com");
        Jwt jwt = jwtDecoder.decode(tokens("dave@example.com").path("accessToken").asText());

        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
    }

    @Test
    void refreshTokenIsStoredOnlyAsSha256Hash() throws Exception {
        register("erin@example.com", PASSWORD);
        String refresh = tokens("erin@example.com").path("refreshToken").asText();

        byte[] stored = jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", byte[].class);
        assertThat(stored).isEqualTo(TokenService.hash(refresh));
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(refresh);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveIdenticalErrors() throws Exception {
        register("frank@example.com", PASSWORD);

        JsonNode wrongPassword = body(login("frank@example.com", "wrong-password-123").andExpect(status().isUnauthorized()));
        JsonNode unknownEmail = body(login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized()));

        for (String field : new String[] {"code", "title", "detail", "status"}) {
            assertThat(unknownEmail.get(field)).as(field).isEqualTo(wrongPassword.get(field));
        }
        assertThat(wrongPassword.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'LOGIN_FAILED'", Long.class)).isEqualTo(2);
    }

    @Test
    void frozenUserCannotLogIn() throws Exception {
        register("gina@example.com", PASSWORD);
        jdbc.update("UPDATE users SET status = 'FROZEN' WHERE email = 'gina@example.com'");

        login("gina@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    // ---- access token --------------------------------------------------------------------

    @Test
    void meReturnsCurrentUser() throws Exception {
        String userId = registerId("hank@example.com");
        String access = tokens("hank@example.com").path("accessToken").asText();

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("hank@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void missingTokenIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId", not(emptyOrNullString())));
    }

    @Test
    void expiredAccessTokenIs401() throws Exception {
        String userId = registerId("ivan@example.com");
        Instant past = Instant.now().minusSeconds(20 * 60);
        String expired = mint(jwtEncoder, userId, "CUSTOMER", past, past.plusSeconds(900)); // expired 5 min ago

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenSignedWithAnotherKeyIs401() throws Exception {
        String userId = registerId("judy@example.com");
        byte[] otherKey = "a-completely-different-32-byte-key!!".getBytes(StandardCharsets.UTF_8);
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
        String forged = mint(forger, userId, "ADMIN", Instant.now(), Instant.now().plusSeconds(900));

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedRoleClaimIs401() throws Exception {
        register("kim@example.com", PASSWORD);
        String[] parts = tokens("kim@example.com").path("accessToken").asText().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String escalated = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("CUSTOMER", "ADMIN").getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + parts[0] + "." + escalated + "." + parts[2]))
                .andExpect(status().isUnauthorized());
    }

    // ---- refresh rotation & reuse detection ----------------------------------------------

    @Test
    void refreshRotatesTokenWithinSameFamily() throws Exception {
        register("liam@example.com", PASSWORD);
        String first = tokens("liam@example.com").path("refreshToken").asText();

        JsonNode rotated = body(refresh(first).andExpect(status().isOk()));
        String second = rotated.path("refreshToken").asText();

        assertThat(second).isNotEqualTo(first);
        assertThat(rotated.path("accessToken").asText()).isNotBlank();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isOk());

        Map<String, Object> old = tokenRow(first);
        Map<String, Object> current = tokenRow(second);
        assertThat(old.get("revoked_at")).isNotNull();
        assertThat(old.get("replaced_by")).isEqualTo(current.get("id"));
        assertThat(current.get("family_id")).isEqualTo(old.get("family_id"));
        assertThat(current.get("revoked_at")).isNull();
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamilyAndIsAudited() throws Exception {
        register("mia@example.com", PASSWORD);
        String first = tokens("mia@example.com").path("refreshToken").asText();
        String second = body(refresh(first)).path("refreshToken").asText();

        // An attacker (or the victim) presents the old, already-rotated token.
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(reuseEvents()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Long.class)).isZero();

        // The newest token in the family is now dead too: both parties must log in again.
        // Presenting it is itself a revoked-token presentation, so it is audited as well.
        refresh(second).andExpect(status().isUnauthorized());
        assertThat(reuseEvents()).isEqualTo(2);
    }

    private long reuseEvents() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED'", Long.class);
    }

    @Test
    void reuseInOneFamilyDoesNotAffectOtherSessions() throws Exception {
        register("noah@example.com", PASSWORD);
        String laptop = tokens("noah@example.com").path("refreshToken").asText();
        String phone = tokens("noah@example.com").path("refreshToken").asText();
        refresh(laptop);

        refresh(laptop).andExpect(status().isUnauthorized()); // reuse in the laptop family

        refresh(phone).andExpect(status().isOk());
    }

    @Test
    void unknownOrExpiredRefreshTokenIs401() throws Exception {
        refresh("this-token-was-never-issued").andExpect(status().isUnauthorized());

        register("olga@example.com", PASSWORD);
        String token = tokens("olga@example.com").path("refreshToken").asText();
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second'");

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void frozenUserCannotRefresh() throws Exception {
        register("pia@example.com", PASSWORD);
        String token = tokens("pia@example.com").path("refreshToken").asText();
        jdbc.update("UPDATE users SET status = 'FROZEN'");

        refresh(token).andExpect(status().isUnauthorized());
    }

    // ---- logout --------------------------------------------------------------------------

    @Test
    void logoutRevokesTheCurrentFamilyOnly() throws Exception {
        register("quinn@example.com", PASSWORD);
        String a = tokens("quinn@example.com").path("refreshToken").asText();
        String b = tokens("quinn@example.com").path("refreshToken").asText();

        postJson("/api/v1/auth/logout", Map.of("refreshToken", a)).andExpect(status().isNoContent());

        refresh(a).andExpect(status().isUnauthorized());
        refresh(b).andExpect(status().isOk());
    }

    @Test
    void logoutWithUnknownTokenStillReturns204() throws Exception {
        postJson("/api/v1/auth/logout", Map.of("refreshToken", "unknown")).andExpect(status().isNoContent());
    }

    @Test
    void logoutAllRevokesEveryFamilyAndRequiresAuthentication() throws Exception {
        register("rosa@example.com", PASSWORD);
        JsonNode session1 = tokens("rosa@example.com");
        String session2Refresh = tokens("rosa@example.com").path("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout-all")).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + session1.path("accessToken").asText()))
                .andExpect(status().isNoContent());

        refresh(session1.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        refresh(session2Refresh).andExpect(status().isUnauthorized());
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions register(String email, String password) throws Exception {
        return postJson("/api/v1/auth/register", Map.of("email", email, "password", password));
    }

    private String registerId(String email) throws Exception {
        return body(register(email, PASSWORD).andExpect(status().isCreated())).path("id").asText();
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private JsonNode tokens(String email) throws Exception {
        return body(login(email, PASSWORD).andExpect(status().isOk()));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> tokenRow(String refreshToken) {
        return jdbc.queryForMap("SELECT id, family_id, revoked_at, replaced_by FROM refresh_tokens WHERE token_hash = ?",
                (Object) TokenService.hash(refreshToken));
    }

    private String mint(JwtEncoder encoder, String subject, String role, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.issuer())
                .subject(subject)
                .claim("role", role)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
