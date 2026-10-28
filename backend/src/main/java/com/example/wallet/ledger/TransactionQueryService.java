package com.example.wallet.ledger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.web.KeysetCursor;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.ledger.TransactionDtos.LegView;
import com.example.wallet.ledger.TransactionDtos.TransactionDetail;
import com.example.wallet.ledger.TransactionDtos.TransactionFilter;
import com.example.wallet.ledger.TransactionDtos.TransactionSummary;

/**
 * Reads transactions for customers (scoped to their own wallets) and for staff (unscoped).
 * Aggregation happens in SQL; Java only maps rows.
 */
@Service
public class TransactionQueryService {

    static final String MASKED_WALLET_NAME = "Another user's wallet";

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountRepository accounts;

    public TransactionQueryService(NamedParameterJdbcTemplate jdbc, AccountRepository accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    // ---- detail --------------------------------------------------------------------------

    /** Visible only if one of the legs touches a wallet the caller owns; otherwise 404. */
    @Transactional(readOnly = true)
    public TransactionDetail detailForCaller(AuthUser caller, UUID transactionId) {
        List<LegRow> legs = legs(transactionId);
        boolean visible = legs.stream().anyMatch(l -> caller.id().equals(l.ownerUserId()));
        if (!visible) {
            throw ApiException.notFound("Transaction");
        }
        return detail(transactionId, legs, caller.id());
    }

    @Transactional(readOnly = true)
    public TransactionDetail detailForStaff(UUID transactionId) {
        List<LegRow> legs = legs(transactionId);
        if (legs.isEmpty()) {
            throw ApiException.notFound("Transaction");
        }
        return detail(transactionId, legs, null);
    }

    private TransactionDetail detail(UUID transactionId, List<LegRow> legs, UUID viewerOrNullForStaff) {
        TransactionDetail header = jdbc.queryForObject("""
                SELECT t.id, t.reference, t.type, t.description, t.created_at, t.reversal_of_transaction_id,
                       (SELECT r.id FROM transactions r WHERE r.reversal_of_transaction_id = t.id) AS reversed_by
                FROM transactions t WHERE t.id = :id
                """, new MapSqlParameterSource("id", transactionId), (rs, n) -> new TransactionDetail(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getObject("reversal_of_transaction_id", UUID.class),
                rs.getObject("reversed_by", UUID.class),
                List.of()));
        List<LegView> legViews = legs.stream().map(l -> {
            boolean foreignWallet = viewerOrNullForStaff != null
                    && l.type() == AccountType.USER_WALLET
                    && !viewerOrNullForStaff.equals(l.ownerUserId());
            return new LegView(l.accountId(), foreignWallet ? MASKED_WALLET_NAME : l.name(), l.type(),
                    l.direction(), l.amount(), l.currency());
        }).toList();
        return new TransactionDetail(header.id(), header.reference(), header.type(), header.description(),
                header.createdAt(), header.reversalOfTransactionId(), header.reversedByTransactionId(), legViews);
    }

    private record LegRow(UUID accountId, String name, AccountType type, UUID ownerUserId,
                          Direction direction, long amount, String currency) {
    }

    private List<LegRow> legs(UUID transactionId) {
        return jdbc.query("""
                SELECT e.account_id, a.name, a.type, a.owner_user_id, e.direction, e.amount, e.currency
                FROM ledger_entries e JOIN accounts a ON a.id = e.account_id
                WHERE e.transaction_id = :id
                ORDER BY e.id
                """, new MapSqlParameterSource("id", transactionId), (rs, n) -> new LegRow(
                rs.getObject("account_id", UUID.class),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("type")),
                rs.getObject("owner_user_id", UUID.class),
                Direction.valueOf(rs.getString("direction")),
                rs.getLong("amount"),
                rs.getString("currency")));
    }

    // ---- lists ---------------------------------------------------------------------------

    /** Transactions touching the caller's wallets (optionally one wallet), newest first. */
    @Transactional(readOnly = true)
    public PageResponse<TransactionSummary> listForCaller(AuthUser caller, TransactionFilter filter,
                                                          String cursor, int limit) {
        if (filter.walletId() != null
                && !accounts.existsByIdAndOwnerUserIdAndType(filter.walletId(), caller.id(), AccountType.USER_WALLET)) {
            throw ApiException.notFound("Wallet");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("caller", caller.id());
        String walletClause = "";
        if (filter.walletId() != null) {
            walletClause = " AND a.id = :walletId";
            params.addValue("walletId", filter.walletId());
        }
        // Start from the caller's own entries, so the query cost grows with their history only.
        String sql = """
                WITH mine AS (
                    SELECT e.transaction_id, e.currency,
                           SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END) AS net
                    FROM ledger_entries e JOIN accounts a ON a.id = e.account_id
                    WHERE a.owner_user_id = :caller AND a.type = 'USER_WALLET'""" + walletClause + """

                    GROUP BY e.transaction_id, e.currency
                )
                SELECT t.id, t.reference, t.type, t.description, t.created_at, t.reversal_of_transaction_id,
                       r.id AS reversed_by, m.currency, m.net,
                       (SELECT SUM(x.amount) FROM ledger_entries x
                         WHERE x.transaction_id = t.id AND x.direction = 'DEBIT' AND x.currency = m.currency) AS amount
                FROM mine m
                JOIN transactions t ON t.id = m.transaction_id
                LEFT JOIN transactions r ON r.reversal_of_transaction_id = t.id
                WHERE TRUE""" + filters(filter, cursor, params) + """

                ORDER BY t.created_at DESC, t.id DESC
                LIMIT :limitPlusOne
                """;
        params.addValue("limitPlusOne", limit + 1);
        return page(jdbc.query(sql, params, SUMMARY), limit);
    }

    /** Any transactions (support/admin), newest first. */
    @Transactional(readOnly = true)
    public PageResponse<TransactionSummary> listForStaff(TransactionFilter filter, String cursor, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String net = "NULL::bigint";
        String walletClause = "";
        if (filter.walletId() != null) {
            params.addValue("walletId", filter.walletId());
            net = "(SELECT SUM(CASE WHEN x.direction = 'CREDIT' THEN x.amount ELSE -x.amount END) "
                    + "FROM ledger_entries x WHERE x.transaction_id = t.id AND x.account_id = :walletId)";
            walletClause = " AND EXISTS (SELECT 1 FROM ledger_entries w WHERE w.transaction_id = t.id AND w.account_id = :walletId)";
        }
        String sql = """
                SELECT t.id, t.reference, t.type, t.description, t.created_at, t.reversal_of_transaction_id,
                       r.id AS reversed_by,
                       (SELECT MIN(x.currency) FROM ledger_entries x WHERE x.transaction_id = t.id) AS currency,
                       (SELECT SUM(x.amount) FROM ledger_entries x WHERE x.transaction_id = t.id AND x.direction = 'DEBIT') AS amount,
                       """ + net + """
                 AS net
                FROM transactions t
                LEFT JOIN transactions r ON r.reversal_of_transaction_id = t.id
                WHERE TRUE""" + walletClause + filters(filter, cursor, params) + """

                ORDER BY t.created_at DESC, t.id DESC
                LIMIT :limitPlusOne
                """;
        params.addValue("limitPlusOne", limit + 1);
        return page(jdbc.query(sql, params, SUMMARY), limit);
    }

    private static String filters(TransactionFilter filter, String cursor, MapSqlParameterSource params) {
        StringBuilder sb = new StringBuilder();
        if (filter.type() != null) {
            sb.append(" AND t.type = :type");
            params.addValue("type", filter.type().name());
        }
        if (filter.from() != null) {
            sb.append(" AND t.created_at >= :from");
            params.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sb.append(" AND t.created_at < :to");
            params.addValue("to", Timestamp.from(filter.to()));
        }
        if (filter.reference() != null && !filter.reference().isBlank()) {
            sb.append(" AND t.reference = :reference");
            params.addValue("reference", filter.reference().trim().toUpperCase());
        }
        KeysetCursor after = KeysetCursor.decode(cursor);
        if (after != null) {
            sb.append(" AND (t.created_at, t.id) < (:cursorAt, :cursorId)");
            params.addValue("cursorAt", after.createdAtTimestamp());
            params.addValue("cursorId", parseUuid(after.id()));
        }
        return sb.toString();
    }

    private static UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(com.example.wallet.common.error.ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }

    private static PageResponse<TransactionSummary> page(List<TransactionSummary> rows, int limit) {
        return PageResponse.of(rows, limit, s -> new KeysetCursor(s.createdAt(), s.id().toString()).encode());
    }

    private static final RowMapper<TransactionSummary> SUMMARY = TransactionQueryService::mapSummary;

    private static TransactionSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        long net = rs.getLong("net");
        Long netAmount = rs.wasNull() ? null : net;
        return new TransactionSummary(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("currency"),
                rs.getLong("amount"),
                netAmount,
                rs.getObject("reversal_of_transaction_id", UUID.class),
                rs.getObject("reversed_by", UUID.class));
    }
}
