package com.cherrycore.shared.infrastructure.security;

/**
 * Rutas que la configuración de seguridad necesita conocer.
 */
public final class SecurityPaths {

    public static final String ALL_PATHS = "/**";
    public static final String HEALTH = "/actuator/health";
    /** Sondas liveness/readiness del orquestador (sin detalles internos). */
    public static final String HEALTH_PROBES = "/actuator/health/*";

    /*
     * API del frontend propio de CherryCore (mismo dominio): sesión con cookie + CSRF.
     * Las apps externas usan el resto de /api con token Bearer.
     */
    public static final String AUTH_API = "/api/auth/**";
    public static final String AUTH_API_PREFIX = "/api/auth/";
    public static final String ACCOUNT_API = "/api/account/**";
    public static final String ACCOUNT_API_PREFIX = "/api/account";
    public static final String COMPANY_API = "/api/company/**";
    public static final String COMPANY_API_ROOT = "/api/company";
    public static final String ADMIN_API = "/api/admin/**";
    /** Marca de la app en el login (GET público, con límite por IP). */
    public static final String APP_BRANDING_PREFIX = "/api/auth/apps/";
    /** Descarga de mis datos (GET con límite por IP). */
    public static final String ACCOUNT_EXPORT = "/api/account/export";

    private SecurityPaths() {
    }
}
