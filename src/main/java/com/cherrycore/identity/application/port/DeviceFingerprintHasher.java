package com.cherrycore.identity.application.port;

import java.util.UUID;

/**
 * Hash con clave del servidor (HMAC) de la huella de un dispositivo: cuenta +
 * id aleatorio de su cookie. Con una copia de la base de datos no se puede
 * saber qué cookie corresponde a cada fila ni reutilizarla en otra cuenta.
 */
public interface DeviceFingerprintHasher {

    String hash(UUID accountId, String deviceId);
}
