package com.example.wallet.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.wallet.idempotency.IdempotencyProperties;

/** Configuration fails fast without secrets, and defaults match the spec. */
class WebhookPropertiesTest {

    @Test
    void missingSecretStopsStartup() {
        assertThatThrownBy(() -> new WebhookProperties("mockpay", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK_SECRET");
        assertThatThrownBy(() -> new WebhookProperties("mockpay", "  ", null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void webhookDefaultsAreTheSpecValues() {
        WebhookProperties props = new WebhookProperties(null, "s", null, null);

        assertThat(props.provider()).isEqualTo("mockpay");
        assertThat(props.tolerance()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void idempotencyDefaultsAreTheSpecValues() {
        IdempotencyProperties props = new IdempotencyProperties(null, null, null);

        assertThat(props.ttl()).isEqualTo(Duration.ofHours(24));
        assertThat(props.staleAfter()).isEqualTo(Duration.ofSeconds(60));
        assertThat(props.inFlightWait()).isEqualTo(Duration.ofSeconds(5));
    }
}
