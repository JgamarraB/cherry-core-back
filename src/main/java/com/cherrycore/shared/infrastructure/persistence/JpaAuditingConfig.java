package com.cherrycore.shared.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.cherrycore.shared.infrastructure.security.CurrentAccount;

/**
 * Rellena automáticamente created_at/by y updated_at/by.
 *
 * <p>El "quién" es la cuenta autenticada; si no hay (registro, procesos
 * automáticos) queda {@code null} = el sistema.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<UUID> auditorAware() {
        return CurrentAccount::id;
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
