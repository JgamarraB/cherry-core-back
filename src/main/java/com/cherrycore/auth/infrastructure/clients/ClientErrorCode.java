package com.cherrycore.auth.infrastructure.clients;

import com.cherrycore.shared.domain.error.ErrorCategory;
import com.cherrycore.shared.domain.error.ErrorCode;

public enum ClientErrorCode implements ErrorCode {

    INVALID_CLIENT_ID(ErrorCategory.INVALID_INPUT, "Client id must be lowercase letters, digits or dashes"),
    INVALID_REDIRECT_URI(ErrorCategory.INVALID_INPUT,
            "Redirect URIs must be absolute https URLs (http://localhost only in development), "
                    + "without wildcards, fragments or credentials"),
    INVALID_SCOPE(ErrorCategory.INVALID_INPUT, "Scope not allowed"),
    RESERVED_APPLICATION(ErrorCategory.INVALID_INPUT, "This application is reserved for CherryCore itself"),
    APPLICATION_REQUIRED(ErrorCategory.INVALID_INPUT, "Staff and machine clients need an application"),
    MACHINE_ACCESS_REQUIRES_CONFIDENTIAL(ErrorCategory.INVALID_INPUT,
            "Machine access is only for confidential clients"),
    SECRET_ONLY_FOR_CONFIDENTIAL(ErrorCategory.BUSINESS_RULE, "Public clients have no secret"),
    CLIENT_NOT_FOUND(ErrorCategory.NOT_FOUND, "Client not found"),
    CLIENT_ID_TAKEN(ErrorCategory.CONFLICT, "Client id already exists");

    private final ErrorCategory category;
    private final String description;

    ClientErrorCode(ErrorCategory category, String description) {
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
