package com.cherrycore.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo entre una cuenta y su usuario en un proveedor social.
 */
public record FederatedIdentity(UUID id, UUID accountId, SocialProvider provider, String subject, String emailAtLink) {

    public FederatedIdentity {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(subject);
    }
}
