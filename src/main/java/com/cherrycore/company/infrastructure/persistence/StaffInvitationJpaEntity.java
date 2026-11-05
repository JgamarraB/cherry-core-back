package com.cherrycore.company.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_invitations")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class StaffInvitationJpaEntity extends AuditableJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected StaffInvitationJpaEntity() {
    }

    public StaffInvitationJpaEntity(UUID id, UUID companyId, String email, UUID roleId, String tokenHash,
            Instant expiresAt) {
        super(id);
        this.companyId = companyId;
        this.email = email;
        this.roleId = roleId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getCompanyId() { return companyId; }
    public String getEmail() { return email; }
    public UUID getRoleId() { return roleId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
