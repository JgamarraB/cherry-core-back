package com.cherrycore.identity.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.identity.domain.OneTimeToken;
import com.cherrycore.identity.domain.OneTimeTokenPurpose;

public interface OneTimeTokenRepository {

    void save(OneTimeToken token);

    /**
     * Busca y bloquea el token: dos peticiones simultáneas no pueden usarlo dos veces.
     */
    Optional<OneTimeToken> findByHashForUpdate(String tokenHash);

    /**
     * Marca como usados los tokens pendientes de una cuenta para un propósito
     * (al pedir uno nuevo, el anterior deja de valer).
     */
    void invalidatePending(UUID accountId, OneTimeTokenPurpose purpose, Instant now);
}
