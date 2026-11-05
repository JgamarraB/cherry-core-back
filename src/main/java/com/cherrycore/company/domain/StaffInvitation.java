package com.cherrycore.company.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Invitación a unirse a una empresa con un rol. El enlace del email lleva un
 * token aleatorio; aquí solo se guarda su hash.
 */
public class StaffInvitation {

    private final UUID id;
    private final UUID companyId;
    private final EmailAddress email;
    private final UUID roleId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant acceptedAt;
    private Instant revokedAt;

    public StaffInvitation(UUID id, UUID companyId, EmailAddress email, UUID roleId, String tokenHash,
            Instant expiresAt, Instant acceptedAt, Instant revokedAt) {
        this.id = Objects.requireNonNull(id);
        this.companyId = Objects.requireNonNull(companyId);
        this.email = Objects.requireNonNull(email);
        this.roleId = Objects.requireNonNull(roleId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
    }

    public boolean isPendingAt(Instant now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void accept(Instant now) {
        if (!isPendingAt(now)) {
            throw new DomainException(CompanyErrorCode.INVALID_OR_EXPIRED_INVITATION);
        }
        acceptedAt = now;
    }

    public void revoke(Instant now) {
        if (acceptedAt == null && revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public EmailAddress email() {
        return email;
    }

    public UUID roleId() {
        return roleId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
