package com.cherrycore.identity.infrastructure.web;

/**
 * Rutas de la API pública de autenticación (la usa el frontend de login).
 */
public final class AuthPaths {

    public static final String BASE = "/api/auth";
    public static final String ALL = BASE + "/**";

    public static final String CSRF = "/csrf";
    public static final String SESSION = "/session";
    public static final String LOGOUT = "/logout";
    public static final String CUSTOMER_LOGIN = "/customers/login";
    public static final String STAFF_LOGIN = "/staff/login";
    public static final String MFA_VERIFY = "/mfa/verify";
    public static final String MFA_SETUP = "/mfa/setup";
    public static final String MFA_SETUP_CONFIRM = "/mfa/setup/confirm";
    public static final String STAFF_TENANTS = "/staff/tenants";
    public static final String STAFF_TENANT = "/staff/tenant";
    public static final String COMPANY_REGISTER = "/companies/register";
    public static final String STAFF_RESEND_VERIFICATION = "/staff/email-verification/resend";
    public static final String STAFF_FORGOT_PASSWORD = "/staff/password/forgot";
    public static final String INVITATION_LOOKUP = "/invitations/lookup";
    public static final String INVITATION_ACCEPT = "/invitations/accept";
    public static final String EMAIL_CHANGE_CONFIRM = "/email-change/confirm";
    public static final String SOCIAL_PROVIDERS = "/social/providers";
    public static final String SOCIAL_PENDING = "/social/pending";
    public static final String SOCIAL_COMPLETE = "/social/complete";
    public static final String CUSTOMER_REGISTER = "/customers/register";
    public static final String CUSTOMER_RESEND_VERIFICATION = "/customers/email-verification/resend";
    public static final String CUSTOMER_FORGOT_PASSWORD = "/customers/password/forgot";
    public static final String CONFIRM_EMAIL = "/email-verification/confirm";
    public static final String RESET_PASSWORD = "/password/reset";

    private AuthPaths() {
    }
}
