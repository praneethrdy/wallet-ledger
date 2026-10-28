package com.example.wallet.auth.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Create and view your OWN wallets: CUSTOMER and ADMIN (SUPPORT holds no wallets).
 * Ownership is still checked in the service; this only gates the role.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public @interface CanHoldWallets {
}
