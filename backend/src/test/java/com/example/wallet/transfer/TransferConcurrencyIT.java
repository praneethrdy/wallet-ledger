package com.example.wallet.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.testsupport.AbstractIntegrationTest;
import com.example.wallet.testsupport.TestLedger;
import com.example.wallet.testsupport.TestUsers;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.example.wallet.user.Role;

/**
 * Spec §9 tests 1 and 2. Threads call TransferService directly (each call is its own DB
 * transaction), released together by a start gate so they really contend for the same rows.
 */
class TransferConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    TransferService transfers;

    @Autowired
    TestUsers users;

    @Autowired
    TestLedger ledger;

    @Test
    void fiftyParallelTransfersNeverOverspend() throws Exception {
        TestUser alice = users.create(Role.CUSTOMER);
        TestUser bob = users.create(Role.CUSTOMER);
        UUID a = ledger.fundedWallet(alice, 100_000); // ₹1,000
        UUID b = ledger.wallet(bob);
        AuthUser caller = new AuthUser(alice.id(), Role.CUSTOMER);

        Map<String, AtomicInteger> outcomes = runConcurrently(50, i ->
                transfers.transfer(caller, new TransferRequest(a, b, 10_000, "INR", "race " + i), null));

        assertThat(outcomes.get("SUCCESS")).hasValue(10);
        assertThat(outcomes.get("INSUFFICIENT_FUNDS")).hasValue(40);
        assertThat(outcomes.keySet()).containsOnly("SUCCESS", "INSUFFICIENT_FUNDS");
        assertThat(ledger.balance(a)).isZero();
        assertThat(ledger.balance(b)).isEqualTo(100_000);
        assertThat(ledger.count("SELECT count(*) FROM transactions WHERE type = 'TRANSFER'")).isEqualTo(10);
        assertThat(ledger.globalImbalance()).allSatisfy((currency, imbalance) -> assertThat(imbalance).isZero());
    }

    @Test
    void opposingTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        TestUser alice = users.create(Role.CUSTOMER);
        TestUser bob = users.create(Role.CUSTOMER);
        UUID a = ledger.fundedWallet(alice, 50_000);
        UUID b = ledger.fundedWallet(bob, 50_000);
        AuthUser aliceCaller = new AuthUser(alice.id(), Role.CUSTOMER);
        AuthUser bobCaller = new AuthUser(bob.id(), Role.CUSTOMER);

        // Alternating directions: without ordered locking, A->B and B->A deadlock each other.
        Map<String, AtomicInteger> outcomes = runConcurrently(100, i -> {
            if (i % 2 == 0) {
                transfers.transfer(aliceCaller, new TransferRequest(a, b, 1_000, "INR", null), null);
            } else {
                transfers.transfer(bobCaller, new TransferRequest(b, a, 1_000, "INR", null), null);
            }
        });

        assertThat(outcomes.keySet()).as("no deadlocks or other errors").containsOnly("SUCCESS");
        assertThat(outcomes.get("SUCCESS")).hasValue(100);
        assertThat(ledger.balance(a) + ledger.balance(b)).isEqualTo(100_000);
        assertThat(ledger.balance(a)).isEqualTo(50_000); // 50 each way
        assertThat(ledger.globalImbalance()).allSatisfy((currency, imbalance) -> assertThat(imbalance).isZero());
    }

    interface Task {
        void run(int i) throws Exception;
    }

    /** Runs n tasks on a thread pool, all released at once; counts outcomes by error code. */
    static Map<String, AtomicInteger> runConcurrently(int n, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, AtomicInteger> outcomes = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    String outcome;
                    try {
                        task.run(index);
                        outcome = "SUCCESS";
                    } catch (ApiException e) {
                        outcome = e.code().name();
                    } catch (Exception e) {
                        outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                    outcomes.computeIfAbsent(outcome, k -> new AtomicInteger()).incrementAndGet();
                    return null;
                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return outcomes;
    }
}
