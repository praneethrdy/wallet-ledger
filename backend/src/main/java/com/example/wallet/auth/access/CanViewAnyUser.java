package com.example.wallet.auth.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Read any user's data (support tooling): SUPPORT (read-only) and ADMIN.
 * Every such access must also be written to audit_log.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
public @interface CanViewAnyUser {
}
