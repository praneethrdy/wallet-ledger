package com.example.wallet.dev;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.example.wallet.common.error.ApiException;
import com.example.wallet.deposit.Deposit;
import com.example.wallet.deposit.DepositRepository;
import com.example.wallet.webhook.HmacSignatureVerifier;
import com.example.wallet.webhook.WebhookController;
import com.example.wallet.webhook.WebhookProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * DEV PROFILE ONLY. Plays the payment provider: builds a deposit event, signs it with the
 * webhook secret and POSTs it to this app's real webhook endpoint over HTTP, so the whole
 * verification path runs exactly as it would for the real provider.
 */
@RestController
@Profile("dev")
@RequestMapping("/dev/mock-provider")
@Hidden
public class MockProviderController {

    private final DepositRepository deposits;
    private final WebhookProperties webhookProperties;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final Clock clock;
    private final RestClient http = RestClient.create();

    public MockProviderController(DepositRepository deposits, WebhookProperties webhookProperties,
                                  ObjectMapper objectMapper, Environment environment, Clock clock) {
        this.deposits = deposits;
        this.webhookProperties = webhookProperties;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.clock = clock;
    }

    @PostMapping("/deposits/{providerReference}/complete")
    public ResponseEntity<String> complete(@PathVariable String providerReference,
                                           @RequestParam(defaultValue = "success") String outcome) throws Exception {
        Deposit deposit = deposits.findByProviderReference(providerReference)
                .orElseThrow(() -> ApiException.notFound("Deposit"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("providerReference", providerReference);
        data.put("amount", deposit.getAmount());
        data.put("currency", deposit.getCurrency());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_" + UUID.randomUUID().toString().replace("-", ""));
        event.put("type", "failure".equalsIgnoreCase(outcome) ? "deposit.failed" : "deposit.succeeded");
        event.put("created", clock.instant().getEpochSecond());
        event.put("data", data);

        byte[] body = objectMapper.writeValueAsBytes(event);
        long timestamp = clock.instant().getEpochSecond();
        String signature = HmacSignatureVerifier.sign(webhookProperties.secret(), timestamp, body);
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));

        ResponseEntity<String> response = http.post()
                .uri("http://localhost:" + port + "/api/v1/webhooks/payment-provider")
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebhookController.TIMESTAMP_HEADER, Long.toString(timestamp))
                .header(WebhookController.SIGNATURE_HEADER, signature)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);
        return ResponseEntity.status(response.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(response.getBody());
    }
}
