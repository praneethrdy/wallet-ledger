package com.example.wallet.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.wallet.common.Money;
import com.example.wallet.ledger.LedgerService;
import com.example.wallet.ledger.Posting;
import com.example.wallet.ledger.SystemAccountCode;
import com.example.wallet.ledger.SystemAccounts;
import com.example.wallet.ledger.TransactionType;
import com.example.wallet.reconciliation.ReconciliationDtos.Finding;
import com.example.wallet.reconciliation.ReconciliationDtos.RunResponse;
import com.example.wallet.reconciliation.ReconciliationDtos.Severity;
import com.example.wallet.reconciliation.ReconciliationDtos.Trigger;
import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Spec §9 test 12. Corruption is injected the only way it could happen in real life: through a
 * superuser connection with triggers disabled (session_replication_role = replica).
 */
class ReconciliationIT extends ApiTest {

    @Autowired
    ReconciliationService reconciliation;

    @Autowired
    ReconciliationJob job;

    @Autowired
    LedgerService ledgerService;

    @Autowired
    SystemAccounts systemAccounts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    MeterRegistry meters;

    TestUser alice;
    UUID walletA;
    UUID walletB;

    @BeforeEach
    void realisticActivity() throws Exception {
        alice = customer();
        walletA = ledger.fundedWallet(alice, 50_000);
        walletB = ledger.wallet(customer());
        String txId = api.body(api.post(alice, "/api/v1/transfers",
                Map.of("fromWalletId", walletA, "toWalletId", walletB, "amount", 1_000, "currency", "INR"))).path("id").asText();
        api.post(admin(), "/api/v1/admin/transactions/" + txId + "/reverse", Map.of("reason", "test")).andExpect(status().isCreated());
    }

    @Test
    void passesOnCleanDataAndPersistsTheRun() {
        RunResponse run = reconciliation.run(Trigger.MANUAL);

        assertThat(run.status()).isEqualTo("PASSED");
        assertThat(run.findings()).isEmpty();
        assertThat(run.checkedTransactions()).isEqualTo(3); // deposit, transfer, reversal
        assertThat(run.totalDebits()).isEqualTo(run.totalCredits()).isEqualTo(50_000 + 1_000 + 1_000);
        assertThat(run.finishedAt()).isNotNull();
        assertThat(reconciliation.get(run.id())).isEqualTo(run);
    }

    @Test
    void scheduledJobRecordsItsTrigger() {
        job.nightly();

        assertThat(jdbc.queryForObject("SELECT trigger_type FROM reconciliation_runs", String.class)).isEqualTo("SCHEDULED");
    }

    @Test
    void detectsAnInjectedOneSidedEntry() throws Exception {
        UUID transferId = jdbc.queryForObject("SELECT id FROM transactions WHERE type = 'TRANSFER'", UUID.class);
        double before = meters.counter("ledger.reconciliation.mismatches").count();
        injectAsSuperuser("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency) "
                + "VALUES ('" + transferId + "', '" + walletB + "', 'CREDIT', 777, 'INR')");

        RunResponse run = reconciliation.run(Trigger.MANUAL);

        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(checks(run)).contains("GLOBAL_BALANCE", "TRANSACTION_UNBALANCED");
        Finding unbalanced = run.findings().stream().filter(f -> f.check().equals("TRANSACTION_UNBALANCED")).findFirst().orElseThrow();
        assertThat(unbalanced.details()).containsEntry("transactionId", transferId.toString());
        assertThat(meters.counter("ledger.reconciliation.mismatches").count()).isGreaterThan(before);
    }

    @Test
    void detectsANegativeWallet() throws Exception {
        UUID transferId = jdbc.queryForObject("SELECT id FROM transactions WHERE type = 'TRANSFER'", UUID.class);
        injectAsSuperuser("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency) VALUES "
                + "('" + transferId + "', '" + walletB + "', 'DEBIT', 999999, 'INR'), "
                + "('" + transferId + "', '" + walletA + "', 'CREDIT', 999999, 'INR')");

        RunResponse run = reconciliation.run(Trigger.MANUAL);

        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(checks(run)).containsExactly("NEGATIVE_WALLET");
    }

    @Test
    void detectsATransactionWithNoLegs() throws Exception {
        injectAsSuperuser("INSERT INTO transactions (id, type, reference, initiated_by_user_id) "
                + "VALUES (gen_random_uuid(), 'TRANSFER', 'TXN-INJECTED', '" + alice.id() + "')");

        assertThat(checks(reconciliation.run(Trigger.MANUAL))).containsExactly("TRANSACTION_MISSING_SIDE");
    }

    @Test
    void detectsDepositMoneyWithoutADepositRecord() {
        Money money = Money.of(5_000, "INR");
        new TransactionTemplate(txManager).executeWithoutResult(s -> ledgerService.post(new Posting(
                TransactionType.DEPOSIT, "mystery money", alice.id(), null, null, Map.of(),
                List.of(Posting.Leg.debit(systemAccounts.id(SystemAccountCode.EXTERNAL_FUNDING, "INR"), money),
                        Posting.Leg.credit(walletA, money)))));

        assertThat(checks(reconciliation.run(Trigger.MANUAL))).containsExactly("TRANSACTION_WITHOUT_DEPOSIT");
    }

    @Test
    void detectsASucceededDepositWhoseLedgerEntryDoesNotMatch() {
        jdbc.update("UPDATE deposits SET amount = amount + 1");

        assertThat(checks(reconciliation.run(Trigger.MANUAL))).containsExactly("DEPOSIT_WITHOUT_MATCHING_TRANSACTION");
    }

    @Test
    void stalePendingDepositIsOnlyAWarning() {
        jdbc.update("""
                INSERT INTO deposits (id, wallet_id, amount, currency, provider_reference, status, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, 100, 'INR', 'mock_stale', 'PENDING', now() - interval '25 hours', now())
                """, walletA);

        RunResponse run = reconciliation.run(Trigger.MANUAL);

        assertThat(run.status()).isEqualTo("PASSED");
        assertThat(run.findings()).singleElement()
                .satisfies(f -> {
                    assertThat(f.check()).isEqualTo("STALE_PENDING_DEPOSIT");
                    assertThat(f.severity()).isEqualTo(Severity.WARNING);
                });
    }

    @Test
    void detectsAReversalOfAReversal() throws Exception {
        UUID reversalId = jdbc.queryForObject("SELECT id FROM transactions WHERE type = 'REVERSAL'", UUID.class);
        injectAsSuperuser("UPDATE transactions SET type = 'REVERSAL', reversal_of_transaction_id = '" + reversalId
                + "' WHERE type = 'DEPOSIT'");

        assertThat(checks(reconciliation.run(Trigger.MANUAL))).contains("REVERSAL_OF_REVERSAL");
    }

    @Test
    void manualRunViaApiIsAdminOnlyAndViewableBySupport() throws Exception {
        TestUser admin = admin();
        TestUser support = support();

        api.post(support, "/api/v1/admin/reconciliation/run", null).andExpect(status().isForbidden());
        String runId = api.body(api.post(admin, "/api/v1/admin/reconciliation/run", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))).path("id").asText();

        api.get(support, "/api/v1/admin/reconciliation/runs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(runId));
        api.get(support, "/api/v1/admin/reconciliation/runs/" + runId).andExpect(status().isOk());
        api.get(alice, "/api/v1/admin/reconciliation/runs").andExpect(status().isForbidden());
        api.get(admin, "/api/v1/admin/overview").andExpect(jsonPath("$.lastReconciliation.id").value(runId));
    }

    private void injectAsSuperuser(String sql) throws Exception {
        try (Connection conn = superuserConnection(); Statement st = conn.createStatement()) {
            st.execute("SET session_replication_role = replica"); // triggers off: simulate a bad manual fix
            st.execute(sql);
        }
    }

    private static List<String> checks(RunResponse run) {
        return run.findings().stream().map(Finding::check).distinct().toList();
    }
}
