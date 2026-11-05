package com.cherrycore.shared.infrastructure.web;

import org.springframework.http.HttpStatus;

/**
 * Errores del protocolo HTTP (petición mal formada, método no permitido...).
 *
 * <p>No son errores de negocio, por eso viven en la capa web y llevan su
 * estado HTTP directamente.</p>
 */
public enum WebErrorCode {

    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request body"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Required parameter is missing"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Parameter has an invalid value"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported");

    private final HttpStatus status;
    private final String description;

    WebErrorCode(HttpStatus status, String description) {
        this.status = status;
        this.description = description;
    }

    public String code() {
        return name();
    }

    public String description() {
        return description;
    }

    public HttpStatus status() {
        return status;
    }
}
