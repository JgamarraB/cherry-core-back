package com.cherrycore.identity.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import com.cherrycore.identity.domain.LoginPolicy;
import com.cherrycore.identity.domain.PasswordPolicy;

/**
 * Parámetros del módulo de identidad. Se construyen a partir de la
 * configuración central (CherryCoreProperties) en la capa de infraestructura.
 */
public record IdentitySettings(
        PasswordPolicy passwordPolicy,
        LoginPolicy loginPolicy,
        Duration emailVerificationTtl,
        Duration passwordResetTtl,
        Locale defaultLocale,
        Set<Locale> supportedLocales
) {

    /**
     * Idioma soportado más parecido al pedido; si no hay ninguno, el de por defecto.
     */
    public Locale resolveLocale(Locale requested) {
        if (requested == null) {
            return defaultLocale;
        }
        return supportedLocales.stream()
                .filter(supported -> supported.getLanguage().equals(requested.getLanguage()))
                .findFirst()
                .orElse(defaultLocale);
    }
}
