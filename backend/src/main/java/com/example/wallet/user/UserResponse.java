package com.example.wallet.user;

import java.util.UUID;

/** Public view of a user. Never includes the password hash. */
public record UserResponse(UUID id, String email, Role role, UserStatus status) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus());
    }
}
