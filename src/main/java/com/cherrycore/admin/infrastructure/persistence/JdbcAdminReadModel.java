package com.cherrycore.admin.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.cherrycore.admin.application.AccountSummary;
import com.cherrycore.admin.application.AuditEventView;
import com.cherrycore.admin.application.DashboardView;
import com.cherrycore.admin.application.port.AdminReadModel;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.application.PageResult;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consultas SQL del panel. Todos los filtros van como parámetros (nunca
 * concatenados): no hay inyección SQL posible.
 */
@Repository
class JdbcAdminReadModel implements AdminReadModel {

    private static final String DASHBOARD_SQL = """
            SELECT
              (SELECT COUNT(*) FROM accounts WHERE deleted_at IS NULL AND type = 'CUSTOMER') AS customers,
              (SELECT COUNT(*) FROM accounts WHERE deleted_at IS NULL AND type = 'STAFF') AS staff,
              (SELECT COUNT(*) FROM companies WHERE deleted_at IS NULL AND status = 'ACTIVE') AS companies_active,
              (SELECT COUNT(*) FROM companies WHERE deleted_at IS NULL AND status = 'PENDING_APPROVAL') AS companies_pending,
              (SELECT COUNT(*) FROM companies WHERE deleted_at IS NULL AND status = 'SUSPENDED') AS companies_suspended,
              (SELECT COUNT(*) FROM auth_events WHERE type = 'LOGIN_COMPLETED' AND occurred_at >= :since) AS logins,
              (SELECT COUNT(*) FROM auth_events WHERE type = 'LOGIN_FAILED' AND occurred_at >= :since) AS failed,
              (SELECT COUNT(*) FROM auth_events WHERE type = 'ACCOUNT_LOCKED' AND occurred_at >= :since) AS locked,
              (SELECT COUNT(*) FROM auth_events WHERE type = 'ACCOUNT_REGISTERED' AND occurred_at >= :since) AS registrations
            """;

    private static final String ACCOUNTS_FROM = """
            FROM accounts a
            LEFT JOIN customers c ON c.id = a.id
            LEFT JOIN staff_users s ON s.id = a.id
            WHERE a.deleted_at IS NULL
            """;

    private static final String ACCOUNTS_SELECT = """
            SELECT a.id, a.type, a.email, a.status, a.email_verified_at, a.platform_role, a.locked_until,
                   a.last_login_at, a.created_at,
                   COALESCE(c.first_name, s.first_name) AS first_name,
                   COALESCE(c.last_name, s.last_name) AS last_name
            """;

    private static final String EVENTS_FROM = """
            FROM auth_events e
            LEFT JOIN accounts a ON a.id = e.account_id
            WHERE 1 = 1
            """;

    /*
     * El email de quien actúa y de la cuenta afectada sale de details. Solo se
     * convierte a UUID si tiene formato de UUID (así nunca falla la consulta).
     */
    private static final String EVENTS_SELECT = """
            SELECT e.id, e.account_id, a.email, e.type, e.ip, e.user_agent, e.occurred_at, e.details,
                   (SELECT x.email FROM accounts x WHERE x.id = CASE WHEN e.details ->> 'actorAccountId' ~ :uuid
                        THEN (e.details ->> 'actorAccountId')::uuid END) AS actor_email,
                   (SELECT x.email FROM accounts x WHERE x.id = CASE WHEN e.details ->> 'targetAccountId' ~ :uuid
                        THEN (e.details ->> 'targetAccountId')::uuid END) AS target_email
            """;
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {
    };

    private static final String COUNT = "SELECT COUNT(*) ";
    private static final String PAGE = " LIMIT :limit OFFSET :offset";
    private static final String LIKE = "%";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    JdbcAdminReadModel(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public DashboardView dashboard(Instant since) {
        return jdbc.sql(DASHBOARD_SQL).param("since", Timestamp.from(since))
                .query((rs, row) -> new DashboardView(rs.getLong("customers"), rs.getLong("staff"),
                        rs.getLong("companies_active"), rs.getLong("companies_pending"),
                        rs.getLong("companies_suspended"), rs.getLong("logins"), rs.getLong("failed"),
                        rs.getLong("locked"), rs.getLong("registrations")))
                .single();
    }

    @Override
    public PageResult<AccountSummary> searchAccounts(String type, String status, String search, PageQuery page) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        if (type != null) {
            where.append(" AND a.type = :type");
            params.put("type", type);
        }
        if (status != null) {
            where.append(" AND a.status = :status");
            params.put("status", status);
        }
        if (search != null) {
            where.append(" AND (a.email LIKE :search OR LOWER(COALESCE(c.first_name, s.first_name, '') || ' '"
                    + " || COALESCE(c.last_name, s.last_name, '')) LIKE :search)");
            params.put("search", LIKE + search.toLowerCase(Locale.ROOT) + LIKE);
        }
        long total = jdbc.sql(COUNT + ACCOUNTS_FROM + where).params(params).query(Long.class).single();
        List<AccountSummary> items = jdbc.sql(ACCOUNTS_SELECT + ACCOUNTS_FROM + where
                        + " ORDER BY a.created_at DESC" + PAGE)
                .params(params).param("limit", page.size()).param("offset", page.offset())
                .query(JdbcAdminReadModel::toAccount).list();
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    @Override
    public PageResult<AuditEventView> searchAuditEvents(UUID accountId, String type, Instant from, Instant to,
            PageQuery page) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        if (accountId != null) {
            where.append(" AND e.account_id = :accountId");
            params.put("accountId", accountId);
        }
        if (type != null) {
            where.append(" AND e.type = :type");
            params.put("type", type);
        }
        if (from != null) {
            where.append(" AND e.occurred_at >= :from");
            params.put("from", Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND e.occurred_at < :to");
            params.put("to", Timestamp.from(to));
        }
        long total = jdbc.sql(COUNT + EVENTS_FROM + where).params(params).query(Long.class).single();
        List<AuditEventView> items = new ArrayList<>(jdbc.sql(EVENTS_SELECT + EVENTS_FROM + where
                        + " ORDER BY e.occurred_at DESC" + PAGE)
                .params(params).param("uuid", UUID_PATTERN).param("limit", page.size())
                .param("offset", page.offset())
                .query(this::toEvent)
                .list());
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    private AuditEventView toEvent(ResultSet rs, int row) throws SQLException {
        String details = rs.getString("details");
        return new AuditEventView(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("email"), rs.getString("type"), rs.getString("ip"), rs.getString("user_agent"),
                instant(rs, "occurred_at"), details == null ? Map.of() : jsonMapper.readValue(details, DETAILS_TYPE),
                rs.getString("actor_email"), rs.getString("target_email"));
    }

    private static AccountSummary toAccount(ResultSet rs, int row) throws SQLException {
        return new AccountSummary(rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("email"),
                rs.getString("status"), rs.getTimestamp("email_verified_at") != null,
                SUPER_ADMIN.equals(rs.getString("platform_role")), rs.getString("first_name"),
                rs.getString("last_name"), instant(rs, "locked_until"), instant(rs, "last_login_at"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
