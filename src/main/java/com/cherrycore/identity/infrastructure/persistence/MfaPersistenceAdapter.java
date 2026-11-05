package com.cherrycore.identity.infrastructure.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.identity.application.port.MfaRepository;
import com.cherrycore.identity.domain.MfaFactor;
import com.cherrycore.identity.domain.RecoveryCode;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

@Repository
class MfaPersistenceAdapter implements MfaRepository {

    private final MfaFactorJpaRepository factors;
    private final RecoveryCodeJpaRepository recoveryCodes;
    private final Clock clock;

    MfaPersistenceAdapter(MfaFactorJpaRepository factors, RecoveryCodeJpaRepository recoveryCodes, Clock clock) {
        this.factors = factors;
        this.recoveryCodes = recoveryCodes;
        this.clock = clock;
    }

    @Override
    public Optional<MfaFactor> findFactor(UUID accountId) {
        return factors.findByAccountId(accountId).map(MfaPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<MfaFactor> findFactorForUpdate(UUID accountId) {
        return factors.findWithLockByAccountId(accountId).map(MfaPersistenceAdapter::toDomain);
    }

    @Override
    public void save(MfaFactor factor) {
        MfaFactorJpaEntity entity = factors.findById(factor.id())
                .orElseGet(() -> new MfaFactorJpaEntity(factor.id(), factor.accountId(), factor.encryptedSecret()));
        entity.setSecretEncrypted(factor.encryptedSecret());
        entity.setConfirmedAt(factor.confirmedAt());
        entity.setLastUsedStep(factor.lastUsedStep());
        factors.saveAndFlush(entity);
    }

    @Override
    public void deleteFactor(UUID accountId) {
        factors.findByAccountId(accountId).ifPresent(entity -> {
            entity.markDeleted(clock.instant(), CurrentAccount.id().orElse(null));
            factors.saveAndFlush(entity);
        });
    }

    @Override
    public List<RecoveryCode> findRecoveryCodes(UUID accountId) {
        return recoveryCodes.findByAccountId(accountId).stream()
                .map(entity -> new RecoveryCode(entity.getId(), entity.getAccountId(), entity.getCodeHash(),
                        entity.getUsedAt()))
                .toList();
    }

    @Override
    public void replaceRecoveryCodes(UUID accountId, List<RecoveryCode> codes) {
        recoveryCodes.findByAccountId(accountId).forEach(entity -> {
            entity.markDeleted(clock.instant(), CurrentAccount.id().orElse(null));
            recoveryCodes.save(entity);
        });
        codes.forEach(code -> recoveryCodes.save(
                new RecoveryCodeJpaEntity(code.id(), code.accountId(), code.codeHash())));
        recoveryCodes.flush();
    }

    @Override
    public void save(RecoveryCode code) {
        recoveryCodes.findById(code.id()).ifPresent(entity -> {
            entity.setUsedAt(code.usedAt());
            recoveryCodes.save(entity);
        });
    }

    private static MfaFactor toDomain(MfaFactorJpaEntity entity) {
        return new MfaFactor(entity.getId(), entity.getAccountId(), entity.getSecretEncrypted(),
                entity.getConfirmedAt(), entity.getLastUsedStep());
    }
}
