package com.cherrycore.shared.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * Borra datos que ya no sirven:
 * <ul>
 *   <li>Autorizaciones OAuth2 con todos sus tokens caducados (Spring
 *       Authorization Server no las borra nunca).</li>
 *   <li>Tokens de un solo uso caducados o usados.</li>
 *   <li>Auditoría de seguridad más antigua que el periodo configurado.</li>
 * </ul>
 * Son borrados idempotentes: si hay varias instancias, ejecutarlo a la vez no
 * causa problemas.
 */
@Component
public class DataRetentionJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataRetentionJob.class);
    private static final String LOG_RESULT =
            "Data retention: {} oauth authorizations, {} one-time tokens, {} auth events removed";

    private static final String EXPIRED_AUTHORIZATIONS_SQL = """
            DELETE FROM oauth2_authorization
             WHERE COALESCE(refresh_token_expires_at, access_token_expires_at, authorization_code_expires_at,
                            oidc_id_token_expires_at) < :before
               AND (refresh_token_expires_at IS NULL OR refresh_token_expires_at < :before)
               AND (access_token_expires_at IS NULL OR access_token_expires_at < :before)
               AND (authorization_code_expires_at IS NULL OR authorization_code_expires_at < :before)
            """;

    private static final String OLD_ONE_TIME_TOKENS_SQL = """
            DELETE FROM one_time_tokens WHERE expires_at < :before OR used_at < :before
            """;

    private static final String OLD_AUTH_EVENTS_SQL = "DELETE FROM auth_events WHERE occurred_at < :before";

    private static final String BEFORE = "before";

    private final JdbcClient jdbc;
    private final CherryCoreProperties.Retention retention;
    private final Clock clock;

    public DataRetentionJob(JdbcClient jdbc, CherryCoreProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.retention = properties.retention();
        this.clock = clock;
    }

    @Scheduled(cron = "${cherrycore.retention.cron}", zone = "UTC")
    @Transactional
    public void purge() {
        Instant now = clock.instant();
        Timestamp tokensBefore = Timestamp.from(now.minus(retention.expiredTokensKeep()));
        int authorizations = jdbc.sql(EXPIRED_AUTHORIZATIONS_SQL).param(BEFORE, tokensBefore).update();
        int tokens = jdbc.sql(OLD_ONE_TIME_TOKENS_SQL).param(BEFORE, tokensBefore).update();
        int events = jdbc.sql(OLD_AUTH_EVENTS_SQL)
                .param(BEFORE, Timestamp.from(now.minus(retention.authEventsKeep()))).update();
        LOGGER.info(LOG_RESULT, authorizations, tokens, events);
    }
}
