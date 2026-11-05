package com.cherrycore.identity.infrastructure.notification;

/**
 * Tipos de email del módulo de identidad. {@code key} es el prefijo de sus
 * textos en i18n/emails*.properties.
 */
enum IdentityEmailType {

    EMAIL_VERIFICATION("email.verification"),
    PASSWORD_RESET("email.password-reset"),
    REGISTRATION_ON_EXISTING_ACCOUNT("email.registration-existing"),
    ACCOUNT_LOCKED("email.account-locked"),
    PASSWORD_CHANGED("email.password-changed");

    private static final String SUBJECT_SUFFIX = ".subject";
    private static final String BODY_SUFFIX = ".body";

    private final String key;

    IdentityEmailType(String key) {
        this.key = key;
    }

    String subjectKey() {
        return key + SUBJECT_SUFFIX;
    }

    String bodyKey() {
        return key + BODY_SUFFIX;
    }
}
