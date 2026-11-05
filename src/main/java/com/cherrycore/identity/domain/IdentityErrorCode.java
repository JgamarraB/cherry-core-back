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
    REAUTHENTICATION_REQUIRED(ErrorCategory.UNAUTHENTICATED, "Sign in again to confirm this change"),
    EMAIL_UNCHANGED(ErrorCategory.INVALID_INPUT, "The new email is the same as the current one"),
    EMAIL_ALREADY_IN_USE(ErrorCategory.CONFLICT, "Email address is already in use"),
    SESSION_NOT_FOUND(ErrorCategory.NOT_FOUND, "Session not found"),
    SOCIAL_PROVIDER_NOT_AVAILABLE(ErrorCategory.NOT_FOUND, "Social login provider is not available"),
    SOCIAL_EMAIL_NOT_VERIFIED(ErrorCategory.FORBIDDEN, "The provider did not confirm the email address"),
    SOCIAL_ACCOUNT_NOT_FOUND(ErrorCategory.NOT_FOUND, "No staff account is linked to this social login"),
    NO_PENDING_SOCIAL_SIGNUP(ErrorCategory.BUSINESS_RULE, "No social sign-up in progress"),
    LAST_SUPER_ADMIN(ErrorCategory.BUSINESS_RULE, "The platform must keep at least one super admin"),
    CANNOT_MODIFY_OWN_ACCOUNT(ErrorCategory.BUSINESS_RULE, "You cannot do this on your own account"),
    NOT_A_STAFF_ACCOUNT(ErrorCategory.INVALID_INPUT, "Only staff accounts can be super admins"),
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
