package com.cherrycore.identity.domain;

/**
 * Estado de una cuenta. El bloqueo por intentos fallidos no es un estado:
 * es temporal ({@code lockedUntil}).
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    DISABLED
}
