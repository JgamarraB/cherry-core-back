package com.cherrycore.identity.application.port;

import java.util.UUID;

/**
 * Cierra todas las sesiones de una cuenta: la sesión de login y los
 * refresh tokens emitidos a las apps.
 */
public interface AccountSessionRevoker {

    void revokeAll(UUID accountId);
}
