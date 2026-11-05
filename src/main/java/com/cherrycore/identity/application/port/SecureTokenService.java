package com.cherrycore.identity.application.port;

/**
 * Genera tokens aleatorios para los enlaces de los emails y calcula su hash.
 */
public interface SecureTokenService {

    GeneratedToken generate();

    String hash(String rawToken);

    /**
     * @param rawValue va en el enlace del email (nunca se guarda)
     * @param hash     se guarda en base de datos
     */
    record GeneratedToken(String rawValue, String hash) {
    }
}
