package com.cherrycore.shared.infrastructure.security;

/**
 * Rutas que la configuración de seguridad necesita conocer.
 */
public final class SecurityPaths {

    public static final String ALL_PATHS = "/**";
    public static final String HEALTH = "/actuator/health";

    private SecurityPaths() {
    }
}
