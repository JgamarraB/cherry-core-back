package com.cherrycore.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de auditoría de seguridad.
 *
 * @param accountId {@code null} si el email no corresponde a ninguna cuenta
 */
public record AuthEvent(
        UUID id,
        UUID accountId,
        AuthEventType type,
        String ip,
        String userAgent,
        Instant occurredAt
) {
}
