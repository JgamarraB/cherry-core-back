package com.cherrycore.shared.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * Cookie y duración de la sesión de login (Spring Session en Postgres).
 * Se configura aquí, explícitamente, para no depender de valores por defecto.
 */
@Configuration
public class SessionConfig {

    @Bean
    CookieSerializer cookieSerializer(CherryCoreProperties properties) {
        CherryCoreProperties.Session session = properties.security().session();
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(session.cookieName());
        // Inaccesible desde JavaScript: un XSS no puede robar la sesión.
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(session.secureCookie());
        serializer.setSameSite(session.sameSite());
        return serializer;
    }

    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionTimeout(CherryCoreProperties properties) {
        return repository -> repository.setDefaultMaxInactiveInterval(properties.security().session().timeout());
    }
}
