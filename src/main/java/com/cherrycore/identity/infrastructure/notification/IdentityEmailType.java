package com.cherrycore.identity.infrastructure.notification;

/**
 * Emails del módulo de identidad. {@code key} es el prefijo de sus textos en
 * i18n/emails*.properties.
 */
enum IdentityEmailType {

    EMAIL_VERIFICATION("email.verification"),
    PASSWORD_RESET("email.password-reset"),
    REGISTRATION_ON_EXISTING_ACCOUNT("email.registration-existing"),
    ACCOUNT_LOCKED("email.account-locked"),
    PASSWORD_CHANGED("email.password-changed");

    private final String key;

    IdentityEmailType(String key) {
        this.key = key;
    }

    String key() {
        return key;
    }
}
