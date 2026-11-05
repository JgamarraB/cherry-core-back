package com.cherrycore.shared.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.cherrycore.support.IntegrationTest;

@IntegrationTest
class DataRetentionJobIntegrationTest {

    private static final String INSERT_AUTHORIZATION = """
            INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type,
                                              access_token_expires_at, refresh_token_expires_at)
            VALUES (:id, 'client', 'someone', 'authorization_code', :expires, :expires)
            """;

    @Autowired
    private DataRetentionJob job;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void removesOnlyLongExpiredAuthorizations() {
        String old = UUID.randomUUID().toString();
        String alive = UUID.randomUUID().toString();
        jdbc.sql(INSERT_AUTHORIZATION).param("id", old)
                .param("expires", Timestamp.from(Instant.now().minus(Duration.ofDays(30)))).update();
        jdbc.sql(INSERT_AUTHORIZATION).param("id", alive)
                .param("expires", Timestamp.from(Instant.now().plus(Duration.ofDays(30)))).update();

        job.purge();

        assertThat(count(old)).isZero();
        assertThat(count(alive)).isEqualTo(1);
    }

    private long count(String id) {
        return jdbc.sql("SELECT COUNT(*) FROM oauth2_authorization WHERE id = ?").param(id).query(Long.class).single();
    }
}
