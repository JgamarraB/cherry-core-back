package com.cherrycore.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.cherrycore.identity.domain.AccountType;

import jakarta.persistence.LockModeType;

interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByTypeAndEmail(AccountType type, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountJpaEntity> findWithLockByTypeAndEmail(AccountType type, String email);

    boolean existsByTypeAndEmail(AccountType type, String email);
}
