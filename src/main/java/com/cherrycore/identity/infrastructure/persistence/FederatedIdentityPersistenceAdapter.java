package com.cherrycore.identity.infrastructure.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cherrycore.identity.application.port.FederatedIdentityRepository;
import com.cherrycore.identity.domain.FederatedIdentity;
import com.cherrycore.identity.domain.SocialProvider;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

interface FederatedIdentityJpaRepository extends JpaRepository<FederatedIdentityJpaEntity, UUID> {

    Optional<FederatedIdentityJpaEntity> findByProviderAndProviderSubject(SocialProvider provider, String subject);

    List<FederatedIdentityJpaEntity> findByAccountId(UUID accountId);
}

@Repository
class FederatedIdentityPersistenceAdapter implements FederatedIdentityRepository {

    private final FederatedIdentityJpaRepository jpaRepository;
    private final Clock clock;

    FederatedIdentityPersistenceAdapter(FederatedIdentityJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public Optional<FederatedIdentity> findByProviderAndSubject(SocialProvider provider, String subject) {
        return jpaRepository.findByProviderAndProviderSubject(provider, subject)
                .map(FederatedIdentityPersistenceAdapter::toDomain);
    }

    @Override
    public List<FederatedIdentity> findByAccount(UUID accountId) {
        return jpaRepository.findByAccountId(accountId).stream().map(FederatedIdentityPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void save(FederatedIdentity identity) {
        jpaRepository.saveAndFlush(new FederatedIdentityJpaEntity(identity.id(), identity.accountId(),
                identity.provider(), identity.subject(), identity.emailAtLink()));
    }

    @Override
    public void deleteByAccount(UUID accountId) {
        jpaRepository.findByAccountId(accountId).forEach(entity -> {
            entity.markDeleted(clock.instant(), CurrentAccount.id().orElse(accountId));
            jpaRepository.save(entity);
        });
        jpaRepository.flush();
    }

    private static FederatedIdentity toDomain(FederatedIdentityJpaEntity entity) {
        return new FederatedIdentity(entity.getId(), entity.getAccountId(), entity.getProvider(),
                entity.getProviderSubject(), entity.getEmailAtLink());
    }
}
