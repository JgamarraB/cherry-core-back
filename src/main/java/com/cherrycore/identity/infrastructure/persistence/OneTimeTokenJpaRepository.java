package com.cherrycore.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cherrycore.identity.domain.OneTimeTokenPurpose;

import jakarta.persistence.LockModeType;

interface OneTimeTokenJpaRepository extends JpaRepository<OneTimeTokenJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OneTimeTokenJpaEntity> findWithLockByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE OneTimeTokenJpaEntity t
               SET t.usedAt = :now
             WHERE t.accountId = :accountId
               AND t.purpose = :purpose
               AND t.usedAt IS NULL
            """)
    void invalidatePending(@Param("accountId") UUID accountId, @Param("purpose") OneTimeTokenPurpose purpose,
            @Param("now") Instant now);
}
