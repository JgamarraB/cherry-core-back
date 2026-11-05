package com.cherrycore.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.identity.domain.AccountStatus;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.PlatformRole;
import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class AccountJpaEntity extends AuditableJpaEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private AccountType type;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role")
    private PlatformRole platformRole;

    @Column(name = "locale", nullable = false)
    private String locale;

    protected AccountJpaEntity() {
    }

    public AccountJpaEntity(UUID id, AccountType type, String email) {
        super(id);
        this.type = type;
        this.email = email;
    }

    public AccountType getType() {
        return type;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public PlatformRole getPlatformRole() {
        return platformRole;
    }

    public void setPlatformRole(PlatformRole platformRole) {
        this.platformRole = platformRole;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
