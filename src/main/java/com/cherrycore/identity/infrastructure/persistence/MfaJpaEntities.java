package com.cherrycore.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;


import jakarta.persistence.LockModeType;

interface MfaFactorJpaRepository extends JpaRepository<MfaFactorJpaEntity, UUID> {

    Optional<MfaFactorJpaEntity> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MfaFactorJpaEntity> findWithLockByAccountId(UUID accountId);
}

interface RecoveryCodeJpaRepository extends JpaRepository<RecoveryCodeJpaEntity, UUID> {

    List<RecoveryCodeJpaEntity> findByAccountId(UUID accountId);
}
