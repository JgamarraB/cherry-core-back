package com.cherrycore.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Segundo factor de una cuenta (app autenticadora). Se crea sin confirmar y
 * se confirma cuando el usuario introduce el primer código correcto.
 * El secreto está cifrado.
 */
public class MfaFactor {

    private final UUID id;
    private final UUID accountId;
    private final String encryptedSecret;
    private Instant confirmedAt;
    private Long lastUsedStep;

    public MfaFactor(UUID id, UUID accountId, String encryptedSecret, Instant confirmedAt, Long lastUsedStep) {
        this.id = Objects.requireNonNull(id);
        this.accountId = Objects.requireNonNull(accountId);
        this.encryptedSecret = Objects.requireNonNull(encryptedSecret);
        this.confirmedAt = confirmedAt;
        this.lastUsedStep = lastUsedStep;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public void confirm(Instant now, long step) {
        confirmedAt = now;
        lastUsedStep = step;
    }

    public void recordUse(long step) {
        lastUsedStep = step;
    }

    public OptionalLong lastUsed() {
        return lastUsedStep == null ? OptionalLong.empty() : OptionalLong.of(lastUsedStep);
    }

    public UUID id() {
        return id;
    }

    public UUID accountId() {
        return accountId;
    }

    public String encryptedSecret() {
        return encryptedSecret;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public Long lastUsedStep() {
        return lastUsedStep;
    }
}
