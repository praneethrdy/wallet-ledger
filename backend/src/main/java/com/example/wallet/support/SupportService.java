package com.example.wallet.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.audit.AuditService;
import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.common.web.KeysetCursor;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.ledger.AccountStatus;
import com.example.wallet.ledger.LedgerService;
import com.example.wallet.ledger.Sql;
import com.example.wallet.ledger.TransactionDtos.TransactionDetail;
import com.example.wallet.ledger.TransactionDtos.TransactionFilter;
import com.example.wallet.ledger.TransactionDtos.TransactionSummary;
import com.example.wallet.ledger.TransactionQueryService;
import com.example.wallet.support.SupportDtos.SupportUserView;
import com.example.wallet.support.SupportDtos.SupportWalletView;
import com.example.wallet.support.SupportDtos.WebhookEventView;
import com.example.wallet.user.Role;
import com.example.wallet.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read-only investigation tools for SUPPORT and ADMIN. Nothing here changes customer data, but
 * every call writes an audit row (who looked at whose data, with which filters), so the
 * audit_log insert is the only write, and it commits with the read.
 */
@Service
public class SupportService {

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;
    private final TransactionQueryService transactions;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public SupportService(JdbcTemplate jdbc, LedgerService ledger, TransactionQueryService transactions,
                          AuditService audit, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.transactions = transactions;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<SupportUserView> searchUsers(AuthUser staff, String email, int limit) {
        List<Object> args = new ArrayList<>();
        String where = "";
        if (email != null && !email.isBlank()) {
            where = " WHERE email::text ILIKE ? ESCAPE '\\'";
            args.add(Sql.likeContains(email.trim().toLowerCase()));
        }
        args.add(limit);
        List<SupportUserView> users = jdbc.query(
                "SELECT id, email::text AS email, role, status, created_at FROM users" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT ?", SupportService::mapUser, args.toArray());
        audit.record(staff.id(), "SUPPORT_SEARCH_USERS", "user", null, details("email", email), null);
        return users;
    }

    @Transactional
    public SupportUserView user(AuthUser staff, UUID userId) {
        SupportUserView user = findUser(userId);
        audit.record(staff.id(), "SUPPORT_VIEW_USER", "user", userId, null, null);
        return user;
    }

    @Transactional
    public List<SupportWalletView> userWallets(AuthUser staff, UUID userId) {
        findUser(userId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, name, currency, status, created_at FROM accounts
                WHERE owner_user_id = ? AND type = 'USER_WALLET' ORDER BY created_at
                """, userId);
        Map<UUID, Long> balances = ledger.balancesOf(rows.stream().map(r -> (UUID) r.get("id")).toList());
        audit.record(staff.id(), "SUPPORT_VIEW_USER_WALLETS", "user", userId, null, null);
        return rows.stream().map(r -> new SupportWalletView(
                (UUID) r.get("id"),
                (String) r.get("name"),
                ((String) r.get("currency")).trim(),
                AccountStatus.valueOf((String) r.get("status")),
                balances.get((UUID) r.get("id")),
                ((Timestamp) r.get("created_at")).toInstant())).toList();
    }

    @Transactional
    public PageResponse<TransactionSummary> transactions(AuthUser staff, TransactionFilter filter, String cursor, int limit) {
        PageResponse<TransactionSummary> page = transactions.listForStaff(filter, cursor, limit);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("walletId", filter.walletId());
        details.put("reference", filter.reference());
        details.put("type", filter.type());
        details.put("from", filter.from());
        details.put("to", filter.to());
        audit.record(staff.id(), "SUPPORT_SEARCH_TRANSACTIONS", "account", filter.walletId(), details, null);
        return page;
    }

    /** Any transaction with every leg under its real wallet name (unlike the customer view). */
    @Transactional
    public TransactionDetail transaction(AuthUser staff, UUID transactionId) {
        TransactionDetail detail = transactions.detailForStaff(transactionId);
        audit.record(staff.id(), "SUPPORT_VIEW_TRANSACTION", "transaction", transactionId, null, null);
        return detail;
    }

    @Transactional
    public PageResponse<WebhookEventView> webhookEvents(AuthUser staff, String status, String cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, provider, provider_event_id, event_type, status, error, payload::text AS payload,
                       received_at, processed_at
                FROM webhook_events WHERE TRUE
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        KeysetCursor after = KeysetCursor.decode(cursor);
        if (after != null) {
            sql.append(" AND (received_at, id) < (?, ?)");
            args.add(after.createdAtTimestamp());
            args.add(parseUuid(after.id()));
        }
        sql.append(" ORDER BY received_at DESC, id DESC LIMIT ?");
        args.add(limit + 1);
        List<WebhookEventView> events = jdbc.query(sql.toString(), (rs, n) -> new WebhookEventView(
                rs.getObject("id", UUID.class),
                rs.getString("provider"),
                rs.getString("provider_event_id"),
                rs.getString("event_type"),
                rs.getString("status"),
                rs.getString("error"),
                readJson(rs.getString("payload")),
                rs.getTimestamp("received_at").toInstant(),
                rs.getTimestamp("processed_at") == null ? null : rs.getTimestamp("processed_at").toInstant()),
                args.toArray());
        audit.record(staff.id(), "SUPPORT_VIEW_WEBHOOK_EVENTS", "webhook_event", null, details("status", status), null);
        return PageResponse.of(events, limit, e -> new KeysetCursor(e.receivedAt(), e.id().toString()).encode());
    }

    private SupportUserView findUser(UUID userId) {
        List<SupportUserView> found = jdbc.query(
                "SELECT id, email::text AS email, role, status, created_at FROM users WHERE id = ?",
                SupportService::mapUser, userId);
        if (found.isEmpty()) {
            throw ApiException.notFound("User");
        }
        return found.get(0);
    }

    private static SupportUserView mapUser(ResultSet rs, int n) throws SQLException {
        return new SupportUserView(rs.getObject("id", UUID.class), rs.getString("email"),
                Role.valueOf(rs.getString("role")), UserStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }

    private JsonNode readJson(String json) {
        try {
            return json == null ? null : objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> details(String key, Object value) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(key, value);
        return details;
    }

    private static UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }
}
