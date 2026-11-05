package com.cherrycore.identity.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.cherrycore.identity.application.port.AuthEventRecorder;
import com.cherrycore.identity.domain.AuthEvent;
import com.cherrycore.identity.domain.AuthEventType;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inserta eventos de auditoría con SQL directo: es solo inserción, no
 * necesita entidad JPA. El contexto ({@code details}) se guarda como JSONB.
 */
@Repository
class AuthEventJdbcRecorder implements AuthEventRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO auth_events (id, account_id, type, ip, user_agent, occurred_at, details)
            VALUES (:id, :accountId, :type, :ip, :userAgent, :occurredAt, CAST(:details AS JSONB))
            """;

    private static final String RECENT_SQL = """
            SELECT id, account_id, type, ip, user_agent, occurred_at, details
              FROM auth_events
             WHERE account_id = :accountId
             ORDER BY occurred_at DESC
             LIMIT :limit
            """;

    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    AuthEventJdbcRecorder(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void record(AuthEvent event) {
        jdbcClient.sql(INSERT_SQL)
                .param("id", event.id())
                .param("accountId", event.accountId())
                .param("type", event.type().name())
                .param("ip", event.ip())
                .param("userAgent", event.userAgent())
                .param("occurredAt", Timestamp.from(event.occurredAt()))
                .param("details", event.details().isEmpty() ? null : jsonMapper.writeValueAsString(event.details()))
                .update();
    }

    @Override
    public List<AuthEvent> findRecentByAccount(UUID accountId, int limit) {
        return jdbcClient.sql(RECENT_SQL)
                .param("accountId", accountId)
                .param("limit", limit)
                .query(this::toEvent)
                .list();
    }

    private AuthEvent toEvent(ResultSet rs, int row) throws SQLException {
        String details = rs.getString("details");
        return new AuthEvent(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                AuthEventType.valueOf(rs.getString("type")), rs.getString("ip"), rs.getString("user_agent"),
                rs.getTimestamp("occurred_at").toInstant(),
                details == null ? Map.of() : jsonMapper.readValue(details, DETAILS_TYPE));
    }
}
