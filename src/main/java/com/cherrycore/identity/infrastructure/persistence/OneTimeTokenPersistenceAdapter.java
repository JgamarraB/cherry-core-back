package com.cherrycore.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.identity.application.port.OneTimeTokenRepository;
import com.cherrycore.identity.domain.OneTimeToken;
import com.cherrycore.identity.domain.OneTimeTokenPurpose;

@Repository
class OneTimeTokenPersistenceAdapter implements OneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;

    OneTimeTokenPersistenceAdapter(OneTimeTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OneTimeToken token) {
        OneTimeTokenJpaEntity entity = jpaRepository.findById(token.id())
                .orElseGet(() -> new OneTimeTokenJpaEntity(token.id(), token.accountId(), token.purpose(),
                        token.tokenHash(), token.expiresAt()));
        entity.setUsedAt(token.usedAt());
        jpaRepository.save(entity);
    }

    @Override
    public Optional<OneTimeToken> findByHashForUpdate(String tokenHash) {
        return jpaRepository.findWithLockByTokenHash(tokenHash)
                .map(entity -> new OneTimeToken(entity.getId(), entity.getAccountId(), entity.getPurpose(),
                        entity.getTokenHash(), entity.getExpiresAt(), entity.getUsedAt()));
    }

    @Override
    public void invalidatePending(UUID accountId, OneTimeTokenPurpose purpose, Instant now) {
        jpaRepository.invalidatePending(accountId, purpose, now);
    }
}
