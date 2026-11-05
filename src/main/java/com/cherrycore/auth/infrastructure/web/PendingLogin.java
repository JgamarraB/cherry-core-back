package com.cherrycore.auth.infrastructure.web;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import com.cherrycore.identity.domain.AccountType;

/**
 * Login a medias: la contraseña es correcta pero falta el segundo factor.
 * Vive en la sesión; NO cuenta como autenticado para nada.
 */
record PendingLogin(UUID accountId, AccountType type, boolean superAdmin, PrimaryFactor primaryFactor,
        int failedMfaAttempts)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String SESSION_ATTRIBUTE = "cherrycore.pendingLogin";

    PendingLogin withFailedAttempt() {
        return new PendingLogin(accountId, type, superAdmin, primaryFactor, failedMfaAttempts + 1);
    }
}
