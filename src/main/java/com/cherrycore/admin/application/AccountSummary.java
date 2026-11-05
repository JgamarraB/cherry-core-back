package com.cherrycore.admin.application;

import java.time.Instant;
import java.util.UUID;

public record AccountSummary(
        UUID id,
        String type,
        String email,
        String status,
        boolean emailVerified,
        boolean superAdmin,
        String firstName,
        String lastName,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt
) {
}
