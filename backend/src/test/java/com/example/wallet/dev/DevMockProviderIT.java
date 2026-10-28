package com.example.wallet.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.deposit.DepositDtos.CreateDepositRequest;
import com.example.wallet.deposit.DepositDtos.DepositResponse;
import com.example.wallet.deposit.DepositService;
import com.example.wallet.deposit.DepositStatus;
import com.example.wallet.testsupport.AbstractIntegrationTest;
import com.example.wallet.testsupport.TestLedger;
import com.example.wallet.testsupport.TestUsers;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.example.wallet.user.Role;

/**
 * The dev mock provider signs an event and POSTs it over real HTTP to the app's own webhook
 * endpoint, so this runs on a real port with the dev profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev"})
class DevMockProviderIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    DepositService deposits;

    @Autowired
    TestUsers users;

    @Autowired
    TestLedger ledger;

    @Test
    void completingADepositSendsASignedWebhookThatCreditsTheWallet() {
        TestUser alice = users.create(Role.CUSTOMER);
        UUID wallet = ledger.wallet(alice);
        AuthUser caller = new AuthUser(alice.id(), Role.CUSTOMER);
        DepositResponse deposit = deposits.create(caller, new CreateDepositRequest(wallet, 25_000, "INR"));

        ResponseEntity<String> response = http.postForEntity(
                "/dev/mock-provider/deposits/" + deposit.providerReference() + "/complete?outcome=success", null, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("processed");
        assertThat(ledger.balance(wallet)).isEqualTo(25_000);
        assertThat(deposits.get(caller, deposit.id()).status()).isEqualTo(DepositStatus.SUCCEEDED);
    }

    @Test
    void failureOutcomeMarksTheDepositFailed() {
        TestUser alice = users.create(Role.CUSTOMER);
        UUID wallet = ledger.wallet(alice);
        AuthUser caller = new AuthUser(alice.id(), Role.CUSTOMER);
        DepositResponse deposit = deposits.create(caller, new CreateDepositRequest(wallet, 1_000, "INR"));

        http.postForEntity("/dev/mock-provider/deposits/" + deposit.providerReference() + "/complete?outcome=failure",
                null, String.class);

        assertThat(deposits.get(caller, deposit.id()).status()).isEqualTo(DepositStatus.FAILED);
        assertThat(ledger.balance(wallet)).isZero();
    }
}
