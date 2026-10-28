package com.example.wallet.auth;

import java.util.UUID;

import com.example.wallet.user.Role;

/**
 * The authenticated caller, taken from the verified JWT. Controllers declare it as a parameter
 * and services receive it. Rule: user identity always comes from here, never from a request body.
 */
public record AuthUser(UUID id, Role role) {

    public boolean hasRole(Role r) {
        return role == r;
    }
}
