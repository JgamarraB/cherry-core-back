package com.cherrycore.identity.application;

import java.time.Instant;

/**
 * Un evento de "Actividad de seguridad" de Mi cuenta.
 *
 * @param ip              {@code null} si lo hizo otra persona (o no se sabe)
 * @param browser         navegador aproximado; {@code null} si no se sabe o lo hizo otra persona
 * @param os              sistema aproximado; ídem
 * @param byAdministrator lo hizo otra persona (un superadmin, el dueño de la empresa...)
 * @param currentDevice   misma IP y navegador que la petición actual ("este dispositivo")
 */
public record SecurityActivityItem(
        String type,
        Instant occurredAt,
        String ip,
        String browser,
        String os,
        boolean byAdministrator,
        boolean currentDevice
) {
}
