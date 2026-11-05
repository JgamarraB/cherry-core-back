package com.cherrycore.identity.domain;

import com.cherrycore.shared.domain.error.ErrorCategory;
import com.cherrycore.shared.domain.error.ErrorCode;

/**
 * Errores del módulo de identidad (cuentas, login, contraseñas).
 */
public enum IdentityErrorCode implements ErrorCode {

    /*
     * Mismo error para "email no existe", "contraseña incorrecta", "cuenta
     * bloqueada" y "cuenta desactivada": así no se puede averiguar qué
     * emails están registrados.
     */
    INVALID_CREDENTIALS(ErrorCategory.UNAUTHENTICATED, "Invalid email or password"),
    EMAIL_NOT_VERIFIED(ErrorCategory.FORBIDDEN, "Email address has not been verified"),
    INVALID_EMAIL(ErrorCategory.INVALID_INPUT, "Email address is not valid"),
    PASSWORD_TOO_SHORT(ErrorCategory.INVALID_INPUT, "Password is too short"),
    PASSWORD_TOO_LONG(ErrorCategory.INVALID_INPUT, "Password is too long"),
    PASSWORD_CONTAINS_EMAIL(ErrorCategory.INVALID_INPUT, "Password must not contain the email address"),
    INVALID_OR_EXPIRED_TOKEN(ErrorCategory.INVALID_INPUT, "Link is invalid or has expired"),
    UNSUPPORTED_LOCALE(ErrorCategory.INVALID_INPUT, "Language is not supported"),
    INVALID_MFA_CODE(ErrorCategory.UNAUTHENTICATED, "Verification code is not valid"),
    MFA_NOT_ENABLED(ErrorCategory.BUSINESS_RULE, "Two-step verification is not enabled"),
    MFA_ALREADY_ENABLED(ErrorCategory.CONFLICT, "Two-step verification is already enabled"),
    MFA_REQUIRED_FOR_STAFF(ErrorCategory.BUSINESS_RULE, "Two-step verification is mandatory for staff accounts"),
    MFA_SETUP_NOT_STARTED(ErrorCategory.BUSINESS_RULE, "Two-step verification setup has not been started"),
    NO_PENDING_LOGIN(ErrorCategory.UNAUTHENTICATED, "No sign-in in progress, sign in again"),
    WRONG_CURRENT_PASSWORD(ErrorCategory.INVALID_INPUT, "Current password is not correct"),
    ACCOUNT_NOT_FOUND(ErrorCategory.NOT_FOUND, "Account not found");

    private final ErrorCategory category;
    private final String description;

    IdentityErrorCode(ErrorCategory category, String description) {
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
