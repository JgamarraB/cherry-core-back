package com.cherrycore.admin.application;

import java.time.Instant;
import java.util.UUID;

public record AuditEventView(UUID id, UUID accountId, String accountEmail, String type, String ip,
        String userAgent, Instant occurredAt) {
}
