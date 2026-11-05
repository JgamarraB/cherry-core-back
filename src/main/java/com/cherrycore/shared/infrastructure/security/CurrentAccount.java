package com.cherrycore.shared.infrastructure.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Id de la cuenta autenticada en la petición actual.
 *
 * <p>Funciona igual con un token JWT (el "sub" es el id de la cuenta) que con
 * la sesión del login (el nombre del principal es el id de la cuenta).</p>
 */
public final class CurrentAccount {

    private CurrentAccount() {
    }

    public static Optional<UUID> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return parse(authentication.getName());
    }

    /**
     * Empresa con la que trabaja la persona de backoffice en esta sesión.
     */
    public static Optional<UUID> activeCompanyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? Optional.empty() : AccountAuthorities.tenantOf(authentication.getAuthorities());
    }

    private static Optional<UUID> parse(String name) {
        try {
            return Optional.of(UUID.fromString(name));
        } catch (IllegalArgumentException notAnAccountId) {
            return Optional.empty();
        }
    }
}
