package com.cherrycore.identity.application;

/**
 * Datos para dar de alta la app autenticadora.
 *
 * @param secret     para escribirlo a mano si no se puede escanear
 * @param otpauthUri el frontend lo convierte en código QR
 */
public record MfaEnrollment(String secret, String otpauthUri) {
}
