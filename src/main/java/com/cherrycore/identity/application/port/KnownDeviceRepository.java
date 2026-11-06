package com.cherrycore.identity.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Navegadores o dispositivos desde los que ya ha entrado cada cuenta. Solo se
 * guarda un hash con clave de la huella, nunca el id de la cookie.
 */
public interface KnownDeviceRepository {

    boolean hasAny(UUID accountId);

    /**
     * Lo añade si no estaba. @return true si es nuevo; false si ya se conocía
     * (entonces solo se actualiza la última vez que se vio).
     */
    boolean addOrTouch(UUID id, UUID accountId, String fingerprintHash, Instant seenAt);

    void deleteByAccount(UUID accountId);
}
