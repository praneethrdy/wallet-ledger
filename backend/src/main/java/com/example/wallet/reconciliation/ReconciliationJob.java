package com.example.wallet.reconciliation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.wallet.reconciliation.ReconciliationDtos.Trigger;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Nightly at 02:00 India time. With several app instances, ShedLock lets exactly one run it;
 * {@code lockAtLeastFor} stops a fast run on one node from letting a slower-clocked node start
 * the same night's run again.
 */
@Component
public class ReconciliationJob {

    private final ReconciliationService reconciliation;

    public ReconciliationJob(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(cron = "${reconciliation.cron:0 0 2 * * *}", zone = "Asia/Kolkata")
    @SchedulerLock(name = "nightly-reconciliation", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void nightly() {
        reconciliation.run(Trigger.SCHEDULED);
    }
}
