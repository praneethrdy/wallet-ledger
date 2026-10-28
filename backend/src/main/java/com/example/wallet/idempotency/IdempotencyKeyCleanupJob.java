package com.example.wallet.idempotency;

import java.sql.Timestamp;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** Daily purge of expired idempotency keys. ShedLock: one instance runs it, not all of them. */
@Component
public class IdempotencyKeyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public IdempotencyKeyCleanupJob(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${idempotency.cleanup-cron:0 30 3 * * *}", zone = "Asia/Kolkata")
    @SchedulerLock(name = "idempotency-key-cleanup", lockAtMostFor = "PT10M")
    public int purgeExpired() {
        int deleted = jdbc.update("DELETE FROM idempotency_keys WHERE expires_at < ?", Timestamp.from(clock.instant()));
        log.info("Idempotency cleanup: {} expired key(s) deleted", deleted);
        return deleted;
    }
}
