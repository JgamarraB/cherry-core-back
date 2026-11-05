package com.cherrycore.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Código de emergencia por si se pierde el móvil. De un solo uso; solo se
 * guarda su hash.
 */
public class RecoveryCode {

    private final UUID id;
    private final UUID accountId;
    private final String codeHash;
    private Instant usedAt;

    public RecoveryCode(UUID id, UUID accountId, String codeHash, Instant usedAt) {
        this.id = Objects.requireNonNull(id);
        this.accountId = Objects.requireNonNull(accountId);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.usedAt = usedAt;
    }

    public boolean isUnused() {
        return usedAt == null;
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

    public String codeHash() {
        return codeHash;
    }

    public Instant usedAt() {
        return usedAt;
    }
}
