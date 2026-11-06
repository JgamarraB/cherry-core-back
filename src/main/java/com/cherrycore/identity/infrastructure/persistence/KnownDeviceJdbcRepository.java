package com.cherrycore.identity.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.cherrycore.identity.application.port.KnownDeviceRepository;

/**
 * Dispositivos conocidos con SQL directo: el alta es "insertar si no existe"
 * (ON CONFLICT), sin carreras entre dos logins a la vez desde el mismo navegador.
 */
@Repository
class KnownDeviceJdbcRepository implements KnownDeviceRepository {

    private static final String HAS_ANY_SQL = """
            SELECT EXISTS (SELECT 1 FROM known_devices WHERE account_id = :accountId AND deleted_at IS NULL)
            """;

    private static final String INSERT_SQL = """
            INSERT INTO known_devices (id, account_id, fingerprint_hash, first_seen_at, last_seen_at)
            VALUES (:id, :accountId, :hash, :seenAt, :seenAt)
            ON CONFLICT (account_id, fingerprint_hash) WHERE deleted_at IS NULL DO NOTHING
            """;

    private static final String TOUCH_SQL = """
            UPDATE known_devices
               SET last_seen_at = :seenAt, updated_at = :seenAt, version = version + 1
             WHERE account_id = :accountId AND fingerprint_hash = :hash AND deleted_at IS NULL
            """;

    private static final String DELETE_SQL = """
            UPDATE known_devices
               SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
             WHERE account_id = :accountId AND deleted_at IS NULL
            """;

    private final JdbcClient jdbcClient;

    KnownDeviceJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean hasAny(UUID accountId) {
        return jdbcClient.sql(HAS_ANY_SQL).param("accountId", accountId).query(Boolean.class).single();
    }

    @Override
    public boolean addOrTouch(UUID id, UUID accountId, String fingerprintHash, Instant seenAt) {
        Timestamp at = Timestamp.from(seenAt);
        int inserted = jdbcClient.sql(INSERT_SQL)
                .param("id", id)
                .param("accountId", accountId)
                .param("hash", fingerprintHash)
                .param("seenAt", at)
                .update();
        if (inserted == 0) {
            jdbcClient.sql(TOUCH_SQL).param("accountId", accountId).param("hash", fingerprintHash)
                    .param("seenAt", at).update();
        }
        return inserted == 1;
    }

    @Override
    public void deleteByAccount(UUID accountId) {
        jdbcClient.sql(DELETE_SQL).param("accountId", accountId).update();
    }
}
