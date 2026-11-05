package com.cherrycore.company.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Empresa (tenant). Se registra pendiente de aprobación; un superadmin la
 * aprueba o la rechaza, y puede suspenderla después.
 */
public class Company {

    public static final int MAX_REJECTION_REASON_LENGTH = 500;

    private final UUID id;
    private CompanyProfileData profile;
    private CompanyStatus status;
    private Instant reviewedAt;
    private UUID reviewedBy;
    private String rejectionReason;
    private String logoKey;

    private Company(UUID id, CompanyProfileData profile, CompanyStatus status, Instant reviewedAt, UUID reviewedBy,
            String rejectionReason, String logoKey) {
        this.id = Objects.requireNonNull(id);
        this.profile = Objects.requireNonNull(profile);
        this.status = Objects.requireNonNull(status);
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
        this.rejectionReason = rejectionReason;
        this.logoKey = logoKey;
    }

    public static Company register(UUID id, CompanyProfileData profile) {
        return new Company(id, profile, CompanyStatus.PENDING_APPROVAL, null, null, null, null);
    }

    public static Company restore(UUID id, CompanyProfileData profile, CompanyStatus status, Instant reviewedAt,
            UUID reviewedBy, String rejectionReason, String logoKey) {
        return new Company(id, profile, status, reviewedAt, reviewedBy, rejectionReason, logoKey);
    }

    public void approve(UUID reviewer, Instant now) {
        requireStatus(CompanyStatus.PENDING_APPROVAL);
        status = CompanyStatus.ACTIVE;
        reviewedAt = now;
        reviewedBy = reviewer;
        rejectionReason = null;
    }

    public void reject(UUID reviewer, Instant now, String reason) {
        requireStatus(CompanyStatus.PENDING_APPROVAL);
        String cleaned = reason == null ? null : reason.strip();
        if (cleaned == null || cleaned.isEmpty() || cleaned.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new DomainException(CompanyErrorCode.REJECTION_REASON_REQUIRED);
        }
        status = CompanyStatus.REJECTED;
        reviewedAt = now;
        reviewedBy = reviewer;
        rejectionReason = cleaned;
    }

    public void suspend() {
        requireStatus(CompanyStatus.ACTIVE);
        status = CompanyStatus.SUSPENDED;
    }

    public void reactivate() {
        requireStatus(CompanyStatus.SUSPENDED);
        status = CompanyStatus.ACTIVE;
    }

    public void updateProfile(CompanyProfileData newProfile) {
        profile = Objects.requireNonNull(newProfile);
    }

    public void changeLogo(String newLogoKey) {
        logoKey = newLogoKey;
    }

    public boolean isActive() {
        return status == CompanyStatus.ACTIVE;
    }

    private void requireStatus(CompanyStatus expected) {
        if (status != expected) {
            throw new DomainException(CompanyErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    public UUID id() {
        return id;
    }

    public CompanyProfileData profile() {
        return profile;
    }

    public CompanyStatus status() {
        return status;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public UUID reviewedBy() {
        return reviewedBy;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public String logoKey() {
        return logoKey;
    }
}
