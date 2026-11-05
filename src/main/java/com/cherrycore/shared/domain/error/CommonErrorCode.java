package com.cherrycore.shared.domain.error;

/**
 * Errores transversales, no ligados a ningún módulo concreto.
 */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR(ErrorCategory.INVALID_INPUT, "Request validation failed"),
    AUTHENTICATION_REQUIRED(ErrorCategory.UNAUTHENTICATED, "Authentication is required"),
    ACCESS_DENIED(ErrorCategory.FORBIDDEN, "Access denied"),
    TOO_MANY_REQUESTS(ErrorCategory.TOO_MANY_REQUESTS, "Too many requests, try again later"),
    INTERNAL_ERROR(ErrorCategory.INTERNAL, "Unexpected internal error");

    private final ErrorCategory category;
    private final String description;

    CommonErrorCode(ErrorCategory category, String description) {
        this.category = category;
        this.description = description;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
