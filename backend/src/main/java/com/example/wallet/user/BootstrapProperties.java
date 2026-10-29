package com.example.wallet.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code bootstrap.*}: logins {@link AccountBootstrap} creates on a fresh deployment. */
@ConfigurationProperties(prefix = "bootstrap")
public record BootstrapProperties(Login admin, Login demo) {

    public record Login(String email, String password) {
        boolean set() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
