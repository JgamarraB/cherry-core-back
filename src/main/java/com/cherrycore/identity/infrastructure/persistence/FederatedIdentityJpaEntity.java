package com.cherrycore.identity.infrastructure.persistence;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.identity.domain.SocialProvider;
import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "federated_identities")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class FederatedIdentityJpaEntity extends AuditableJpaEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false)
    private SocialProvider provider;

    @Column(name = "provider_subject", nullable = false, updatable = false)
    private String providerSubject;

    @Column(name = "email_at_link", updatable = false)
    private String emailAtLink;

    protected FederatedIdentityJpaEntity() {
    }

    public FederatedIdentityJpaEntity(UUID id, UUID accountId, SocialProvider provider, String providerSubject,
            String emailAtLink) {
        super(id);
        this.accountId = accountId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.emailAtLink = emailAtLink;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getEmailAtLink() {
        return emailAtLink;
    }
}
