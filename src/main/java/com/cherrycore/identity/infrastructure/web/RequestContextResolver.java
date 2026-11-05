package com.cherrycore.identity.infrastructure.web;

import org.springframework.http.HttpHeaders;

import com.cherrycore.identity.application.RequestContext;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP y navegador de la petición para la auditoría.
 *
 * <p>La IP es {@code getRemoteAddr()}: con {@code forward-headers-strategy: native}
 * Tomcat ya la sustituye por la del cliente real solo si la petición viene de
 * un proxy interno de confianza (no se puede falsificar con una cabecera).</p>
 */
public final class RequestContextResolver {

    private RequestContextResolver() {
    }

    public static RequestContext from(HttpServletRequest request) {
        return new RequestContext(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
