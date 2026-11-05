package com.cherrycore.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.cherrycore.identity.domain.OneTimeTokenPurpose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla de solo inserción: sin auditoría de actualización ni borrado lógico.
 */
@Entity
@Table(name = "one_time_tokens")
public class OneTimeTokenJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private OneTimeTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "payload", updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected OneTimeTokenJpaEntity() {
    }

    public OneTimeTokenJpaEntity(UUID id, UUID accountId, OneTimeTokenPurpose purpose, String tokenHash,
            Instant expiresAt, String payload) {
        this.payload = payload;
        this.id = id;
        this.accountId = accountId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public OneTimeTokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
