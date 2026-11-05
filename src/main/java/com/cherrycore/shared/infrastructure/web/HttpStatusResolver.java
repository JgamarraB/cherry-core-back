package com.cherrycore.shared.infrastructure.web;

import org.springframework.http.HttpStatus;

import com.cherrycore.shared.domain.error.ErrorCategory;
import com.cherrycore.shared.domain.error.ErrorCode;

/**
 * Único sitio donde se decide el estado HTTP de cada categoría de error.
 *
 * <p>El {@code switch} es exhaustivo: si se añade una categoría nueva y no se
 * mapea aquí, el código no compila.</p>
 */
public final class HttpStatusResolver {

    private HttpStatusResolver() {
    }

    public static HttpStatus resolve(ErrorCode errorCode) {
        return resolve(errorCode.category());
    }

    public static HttpStatus resolve(ErrorCategory category) {
        return switch (category) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
