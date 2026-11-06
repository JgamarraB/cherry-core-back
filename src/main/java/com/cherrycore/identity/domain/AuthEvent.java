package com.cherrycore.identity.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Evento de auditoría de seguridad.
 *
 * @param accountId {@code null} si el email no corresponde a ninguna cuenta
 * @param details   contexto del evento (claves de {@link AuditDetail}); vacío si no lleva.
 *                  Nunca contraseñas, secretos ni tokens.
 */
public record AuthEvent(
        UUID id,
        UUID accountId,
        AuthEventType type,
        String ip,
        String userAgent,
        Instant occurredAt,
        Map<String, String> details
) {

    public AuthEvent {
        details = AuditDetail.sanitize(details);
    }
}
