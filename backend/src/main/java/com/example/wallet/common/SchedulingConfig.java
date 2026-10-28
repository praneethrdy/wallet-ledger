package com.example.wallet.common;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * Scheduled jobs + ShedLock. With N app instances, @Scheduled fires on all N; ShedLock makes
 * them race for a row in the {@code shedlock} table so exactly one runs each job.
 * {@code usingDbTime()}: lock timestamps come from Postgres, so clock skew between app hosts
 * can't let two instances both think the lock has expired.
 *
 * <p>Disabled in tests ({@code app.scheduling.enabled=false}): tests call jobs directly.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
