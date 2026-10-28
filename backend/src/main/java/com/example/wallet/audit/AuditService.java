package com.example.wallet.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.wallet.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Appends to audit_log. Joins the caller's transaction (if any), so an audited action and its
 * audit row commit or roll back together. Never put secrets, tokens or passwords in details.
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void record(UUID actorUserId, String action, String entityType, Object entityId,
                       Map<String, ?> details, String ip) {
        jdbc.update("""
                INSERT INTO audit_log (actor_user_id, action, entity_type, entity_id, details, ip, correlation_id, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """,
                actorUserId, action, entityType, entityId == null ? null : entityId.toString(),
                toJson(details), ip, CorrelationIdFilter.current(),
                Timestamp.from(clock.instant()));
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit details are not serializable", e);
        }
    }
}
