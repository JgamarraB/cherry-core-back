package com.cherrycore.identity.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Proveedores de login social. {@code registrationId} es el nombre en la
 * configuración ({@code spring.security.oauth2.client.registration.google}).
 */
public enum SocialProvider {

    GOOGLE("google"),
    APPLE("apple");

    private final String registrationId;

    SocialProvider(String registrationId) {
        this.registrationId = registrationId;
    }

    public String registrationId() {
        return registrationId;
    }

    public static Optional<SocialProvider> fromRegistrationId(String registrationId) {
        return Arrays.stream(values())
                .filter(provider -> provider.registrationId.equals(
                        registrationId == null ? null : registrationId.toLowerCase(Locale.ROOT)))
                .findFirst();
    }
}
