package com.cherrycore.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Datos de la cuenta para la exportación RGPD (derecho de acceso).
 * Nunca incluye el hash de la contraseña, el secreto MFA ni tokens.
 */
public record IdentityDataExport(
        AccountData account,
        List<SecurityEventData> securityEvents,
        List<LinkedIdentityData> federatedIdentities
) {

    public record AccountData(
            UUID id,
            String type,
            String email,
            Instant emailVerifiedAt,
            String status,
            String locale,
            boolean hasPassword,
            boolean mfaEnabled,
            boolean superAdmin,
            Instant lastLoginAt
    ) {
    }

    public record SecurityEventData(String type, Instant occurredAt, String ip, String userAgent) {
    }

    /**
     * Solo el proveedor y el email con el que se vinculó (no el id interno del proveedor).
     */
    public record LinkedIdentityData(String provider, String email) {
    }
}
