package com.example.wallet.auth;

import com.example.wallet.user.UserResponse;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response bodies for /api/v1/auth. Unknown JSON fields (e.g. "role") are rejected. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Max 128: Argon2 cost grows with input, and nobody needs a longer password. */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {
    }

    /** No min length on login: the rule applies when choosing a password, not when checking one. */
    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record RefreshTokenRequest(@NotBlank @Size(max = 128) String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
    }
}
