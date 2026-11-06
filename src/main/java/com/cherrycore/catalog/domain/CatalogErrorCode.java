package com.cherrycore.catalog.domain;

import com.cherrycore.shared.domain.error.ErrorCategory;
import com.cherrycore.shared.domain.error.ErrorCode;

public enum CatalogErrorCode implements ErrorCode {

    INVALID_APPLICATION_CODE(ErrorCategory.INVALID_INPUT, "Application code must be lowercase letters, digits or dashes"),
    INVALID_PERMISSION_CODE(ErrorCategory.INVALID_INPUT, "Permission code must be lowercase, e.g. vehicles.manage"),
    APPLICATION_NOT_FOUND(ErrorCategory.NOT_FOUND, "Application not found"),
    APPLICATION_CODE_TAKEN(ErrorCategory.CONFLICT, "Application code already exists"),
    APPLICATION_DISABLED(ErrorCategory.BUSINESS_RULE, "Application is disabled"),
    UNKNOWN_PERMISSION(ErrorCategory.INVALID_INPUT, "Permission does not exist"),
    TOO_MANY_PERMISSIONS(ErrorCategory.INVALID_INPUT, "Too many permissions in one request"),
    // Mismos códigos que el logo de empresa: el frontend usa la misma traducción.
    LOGO_INVALID_FORMAT(ErrorCategory.INVALID_INPUT, "Logo must be a PNG, JPEG or WEBP image"),
    LOGO_TOO_LARGE(ErrorCategory.INVALID_INPUT, "Logo file is too large");

    private final ErrorCategory category;
    private final String description;

    CatalogErrorCode(ErrorCategory category, String description) {
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
