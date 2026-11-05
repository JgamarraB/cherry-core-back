package com.cherrycore.shared.infrastructure.security;

/**
 * Nombres usados por la protección CSRF del frontend de login (valores por
 * defecto de Spring, que Angular ya entiende sin configurar nada).
 *
 * <p>El frontend lee la cookie {@link #COOKIE_NAME} y envía su valor en la
 * cabecera {@link #HEADER_NAME} en cada POST/PUT/DELETE.</p>
 */
public final class CsrfSettings {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private CsrfSettings() {
    }
}
