package com.cherrycore.shared.infrastructure.security;

/**
 * Valores de las cabeceras de seguridad.
 *
 * <p>La API solo devuelve JSON, así que la política de contenido prohíbe
 * cargar cualquier recurso y que la respuesta se muestre dentro de un iframe.</p>
 */
public final class SecurityHeaders {

    public static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";
    public static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";

    private SecurityHeaders() {
    }
}
