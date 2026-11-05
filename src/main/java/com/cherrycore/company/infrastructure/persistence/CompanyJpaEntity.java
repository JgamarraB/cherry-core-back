package com.cherrycore.company.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class CompanyJpaEntity extends AuditableJpaEntity {

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name", nullable = false)
    private String tradeName;

    @Column(name = "tax_id", nullable = false)
    private String taxId;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompanyStatus status;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "logo_key")
    private String logoKey;

    protected CompanyJpaEntity() {
    }

    public CompanyJpaEntity(UUID id) {
        super(id);
    }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public CompanyStatus getStatus() { return status; }
    public void setStatus(CompanyStatus status) { this.status = status; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getLogoKey() { return logoKey; }
    public void setLogoKey(String logoKey) { this.logoKey = logoKey; }
}
