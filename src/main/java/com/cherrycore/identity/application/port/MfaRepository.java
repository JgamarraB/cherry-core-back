package com.cherrycore.identity.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.identity.domain.MfaFactor;
import com.cherrycore.identity.domain.RecoveryCode;

public interface MfaRepository {

    Optional<MfaFactor> findFactor(UUID accountId);

    /**
     * Bloquea la fila: dos peticiones simultáneas no pueden usar el mismo código.
     */
    Optional<MfaFactor> findFactorForUpdate(UUID accountId);

    void save(MfaFactor factor);

    void deleteFactor(UUID accountId);

    List<RecoveryCode> findRecoveryCodes(UUID accountId);

    void replaceRecoveryCodes(UUID accountId, List<RecoveryCode> codes);

    void save(RecoveryCode code);
}
