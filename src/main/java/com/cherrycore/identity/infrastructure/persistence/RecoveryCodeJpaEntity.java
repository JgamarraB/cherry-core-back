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
@Table(name = "mfa_recovery_codes")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class RecoveryCodeJpaEntity extends AuditableJpaEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCodeJpaEntity() {
    }

    public RecoveryCodeJpaEntity(UUID id, UUID accountId, String codeHash) {
        super(id);
        this.accountId = accountId;
        this.codeHash = codeHash;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
