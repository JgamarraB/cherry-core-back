package com.cherrycore.admin.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Evento de auditoría para el panel.
 *
 * @param details     contexto del evento (empresa, app, rol...); vacío si no lleva
 * @param actorEmail  email de quien hizo la acción ({@code details.actorAccountId}), si existe
 * @param targetEmail email de la cuenta afectada ({@code details.targetAccountId}), si existe
 */
public record AuditEventView(UUID id, UUID accountId, String accountEmail, String type, String ip,
        String userAgent, Instant occurredAt, Map<String, String> details, String actorEmail, String targetEmail) {
}
