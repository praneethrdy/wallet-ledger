package com.example.wallet.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code payments.demo.*}
 *
 * @param enabled lets customers complete their own deposits with a simulated payment. On in dev
 *                and tests; in production only for a public demo (env DEMO_PAYMENTS_ENABLED=true),
 *                because it lets any customer create money.
 */
@ConfigurationProperties(prefix = "payments.demo")
public record DemoPaymentProperties(boolean enabled) {
}
