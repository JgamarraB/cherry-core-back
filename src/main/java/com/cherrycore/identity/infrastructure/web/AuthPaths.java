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
    public static final String CUSTOMER_REGISTER = "/customers/register";
    public static final String CUSTOMER_RESEND_VERIFICATION = "/customers/email-verification/resend";
    public static final String CUSTOMER_FORGOT_PASSWORD = "/customers/password/forgot";
    public static final String CONFIRM_EMAIL = "/email-verification/confirm";
    public static final String RESET_PASSWORD = "/password/reset";

    private AuthPaths() {
    }
}
