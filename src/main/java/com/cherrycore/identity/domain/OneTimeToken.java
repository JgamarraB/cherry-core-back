package com.cherrycore.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Token de un solo uso enviado por email. Solo se guarda su hash: si alguien
 * lee la base de datos no puede usar los enlaces.
 */
public class OneTimeToken {

    private final UUID id;
    private final UUID accountId;
    private final OneTimeTokenPurpose purpose;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant usedAt;

    public OneTimeToken(UUID id, UUID accountId, OneTimeTokenPurpose purpose, String tokenHash,
            Instant expiresAt, Instant usedAt) {
        this.id = Objects.requireNonNull(id);
        this.accountId = Objects.requireNonNull(accountId);
        this.purpose = Objects.requireNonNull(purpose);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.usedAt = usedAt;
    }

    public boolean isUsableFor(OneTimeTokenPurpose expectedPurpose, Instant now) {
        return purpose == expectedPurpose && usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID accountId() {
        return accountId;
    }

    public OneTimeTokenPurpose purpose() {
        return purpose;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }
}
