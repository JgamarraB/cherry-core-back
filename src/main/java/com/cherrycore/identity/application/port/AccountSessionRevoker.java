package com.cherrycore.identity.application.port;

import java.util.UUID;

/**
 * Cierra todas las sesiones de una cuenta: la sesión de login y los
 * refresh tokens emitidos a las apps.
 */
public interface AccountSessionRevoker {

    void revokeAll(UUID accountId);

    /**
     * Igual, pero mantiene la sesión desde la que se hace el cambio.
     *
     * @param keepSessionId id de la sesión actual (opaco para la aplicación)
     */
    void revokeAllExcept(UUID accountId, String keepSessionId);
}
