package com.cherrycore.identity.infrastructure.persistence;

import java.sql.Timestamp;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.cherrycore.identity.application.port.AuthEventRecorder;
import com.cherrycore.identity.domain.AuthEvent;

/**
 * Inserta eventos de auditoría con SQL directo: es solo inserción, no
 * necesita entidad JPA.
 */
@Repository
class AuthEventJdbcRecorder implements AuthEventRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO auth_events (id, account_id, type, ip, user_agent, occurred_at)
            VALUES (:id, :accountId, :type, :ip, :userAgent, :occurredAt)
            """;

    private final JdbcClient jdbcClient;

    AuthEventJdbcRecorder(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
                .update();
    }
}
