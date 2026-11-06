package com.cherrycore.identity.application.port;

/**
 * Hash de los códigos de recuperación MFA. Lleva una clave del servidor
 * (HMAC): con solo una copia de la base de datos no se pueden probar códigos
 * por fuerza bruta (tienen unos 49 bits, demasiado pocos para un hash sin clave).
 */
public interface RecoveryCodeHasher {

    /**
     * @param normalizedCode código ya normalizado (sin guiones, en minúsculas)
     * @return hash en hexadecimal
     */
    String hash(String normalizedCode);
}
