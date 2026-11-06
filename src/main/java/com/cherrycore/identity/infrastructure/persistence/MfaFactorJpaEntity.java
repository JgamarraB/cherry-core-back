package com.cherrycore.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mfa_factors")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class MfaFactorJpaEntity extends AuditableJpaEntity {

    static final String TYPE_TOTP = "TOTP";

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "type", nullable = false, updatable = false)
    private String type;

    // Actualizable: al rotar la clave de cifrado se vuelve a cifrar con la nueva.
    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    protected MfaFactorJpaEntity() {
    }

    public MfaFactorJpaEntity(UUID id, UUID accountId, String secretEncrypted) {
        super(id);
        this.accountId = accountId;
        this.type = TYPE_TOTP;
        this.secretEncrypted = secretEncrypted;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public void setSecretEncrypted(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Long getLastUsedStep() {
        return lastUsedStep;
    }

    public void setLastUsedStep(Long lastUsedStep) {
        this.lastUsedStep = lastUsedStep;
    }
}
