package com.example.wallet.auth.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Transfer or deposit from your own wallet: CUSTOMER only.
 * ADMIN is deliberately excluded: staff must not move customer money through customer endpoints.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('CUSTOMER')")
public @interface CanMoveMoney {
}
