package com.cherrycore.auth.infrastructure.web;

/**
 * Siguiente paso del login que debe mostrar el frontend.
 */
public enum LoginStatus {
    /** Sesión iniciada. Si hay {@code redirectUrl}, ir a ella. */
    AUTHENTICATED,
    /** Pedir el código de la app autenticadora (o un código de recuperación). */
    MFA_REQUIRED,
    /** Primera vez en backoffice: configurar la app autenticadora. */
    MFA_SETUP_REQUIRED,
    /** Varias empresas: elegir con cuál entrar. */
    TENANT_SELECTION_REQUIRED
}
