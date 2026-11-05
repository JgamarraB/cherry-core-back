package com.cherrycore.identity.domain;

/**
 * Eventos de seguridad que se guardan en auth_events.
 */
public enum AuthEventType {
    ACCOUNT_REGISTERED,
    REGISTRATION_ON_EXISTING_EMAIL,
    EMAIL_VERIFIED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGIN_BLOCKED_WHILE_LOCKED,
    LOGIN_UNVERIFIED_EMAIL,
    ACCOUNT_LOCKED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    LOGOUT
}
