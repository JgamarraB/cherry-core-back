package com.cherrycore.shared.domain.error;

/**
 * Errores transversales, no ligados a ningún módulo concreto.
 */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR(ErrorCategory.INVALID_INPUT, "Request validation failed"),
    TERMS_NOT_ACCEPTED(ErrorCategory.INVALID_INPUT, "Terms and conditions must be accepted"),
    INVALID_NAME(ErrorCategory.INVALID_INPUT, "Name is not valid"),
    INVALID_PHONE(ErrorCategory.INVALID_INPUT, "Phone number must be in international format, e.g. +34600111222"),
    INVALID_COUNTRY_CODE(ErrorCategory.INVALID_INPUT, "Country code must be ISO 3166-1 alpha-2, e.g. ES"),
    IMAGE_UNREADABLE(ErrorCategory.INVALID_INPUT, "The image is damaged or is not a valid image"),
    IMAGE_DIMENSIONS_TOO_LARGE(ErrorCategory.INVALID_INPUT, "The image width or height is too large"),
    AUTHENTICATION_REQUIRED(ErrorCategory.UNAUTHENTICATED, "Authentication is required"),
    ACCESS_DENIED(ErrorCategory.FORBIDDEN, "Access denied"),
    CSRF_TOKEN_INVALID(ErrorCategory.FORBIDDEN, "CSRF token is missing or invalid"),
    CONCURRENT_MODIFICATION(ErrorCategory.CONFLICT, "The resource was modified by another request, reload and retry"),
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
