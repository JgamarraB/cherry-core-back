package com.cherrycore.identity.infrastructure.security;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import com.cherrycore.identity.application.port.AccountSessionRevoker;

/**
 * Cierra la sesión de login (Spring Session) y elimina las autorizaciones
 * OAuth2 de la cuenta, con lo que sus refresh tokens dejan de valer.
 * Los access tokens ya emitidos caducan solos en pocos minutos.
 */
@Component
class SpringSessionAccountSessionRevoker implements AccountSessionRevoker {

    private static final String DELETE_AUTHORIZATIONS_SQL =
            "DELETE FROM oauth2_authorization WHERE principal_name = :principal";

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final JdbcClient jdbcClient;

    SpringSessionAccountSessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessionRepository,
            JdbcClient jdbcClient) {
        this.sessionRepository = sessionRepository;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void revokeAll(UUID accountId) {
        String principal = accountId.toString();
        sessionRepository.findByPrincipalName(principal).keySet().forEach(sessionRepository::deleteById);
        jdbcClient.sql(DELETE_AUTHORIZATIONS_SQL).param("principal", principal).update();
    }
}
