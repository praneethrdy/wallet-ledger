package com.example.wallet.webhook;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code webhook.payment-provider.*}. Secrets come from the environment in prod
 * (WEBHOOK_SECRET, WEBHOOK_PREVIOUS_SECRET).
 *
 * @param provider       name stored with each event (namespace for provider event ids)
 * @param secret         current signing secret
 * @param previousSecret still accepted during rotation; blank when not rotating
 * @param tolerance      max |now - X-Webhook-Timestamp| (spec: 5 min)
 */
@ConfigurationProperties(prefix = "webhook.payment-provider")
public record WebhookProperties(String provider, String secret, String previousSecret, Duration tolerance) {

    public WebhookProperties {
        if (provider == null || provider.isBlank()) {
            provider = "mockpay";
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("webhook.payment-provider.secret (env WEBHOOK_SECRET) is required");
        }
        if (tolerance == null) {
            tolerance = Duration.ofMinutes(5);
        }
    }
}
