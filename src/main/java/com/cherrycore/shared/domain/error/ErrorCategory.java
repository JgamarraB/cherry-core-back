package com.cherrycore.shared.domain.error;

/**
 * Naturaleza de un error de negocio.
 *
 * <p>El dominio no conoce HTTP: solo dice qué tipo de error es.
 * La capa web traduce cada categoría a su estado HTTP en un único sitio
 * ({@code HttpStatusResolver}).</p>
 */
public enum ErrorCategory {
    INVALID_INPUT,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    BUSINESS_RULE,
    TOO_MANY_REQUESTS,
    INTERNAL
}
