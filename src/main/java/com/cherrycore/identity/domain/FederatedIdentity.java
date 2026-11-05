package com.cherrycore.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo entre una cuenta y su usuario en un proveedor social.
 */
/**
 * @param accountType el mismo usuario de Google puede tener cuenta de cliente
 *                    y de backoffice (son cuentas distintas)
 */
public record FederatedIdentity(UUID id, UUID accountId, AccountType accountType, SocialProvider provider,
        String subject, String emailAtLink) {

    public FederatedIdentity {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(accountType);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(subject);
    }
}
