package com.cherrycore.identity.infrastructure.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.PlatformRole;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

@Repository
class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;
    private final Clock clock;

    AccountPersistenceAdapter(AccountJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpaRepository.findById(id).map(AccountPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        return jpaRepository.findWithLockById(id).map(AccountPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Account> findByTypeAndEmail(AccountType type, EmailAddress email) {
        return jpaRepository.findByTypeAndEmail(type, email.value()).map(AccountPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Account> findByTypeAndEmailForUpdate(AccountType type, EmailAddress email) {
        return jpaRepository.findWithLockByTypeAndEmail(type, email.value()).map(AccountPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByTypeAndEmail(AccountType type, EmailAddress email) {
        return jpaRepository.existsByTypeAndEmail(type, email.value());
    }

    @Override
    public List<Account> findSuperAdmins() {
        return jpaRepository.findByPlatformRole(PlatformRole.SUPER_ADMIN).stream()
                .map(AccountPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void save(Account account) {
        AccountJpaEntity entity = jpaRepository.findById(account.id())
                .orElseGet(() -> new AccountJpaEntity(account.id(), account.type(), account.email().value()));
        entity.setEmail(account.email().value());
        entity.setEmailVerifiedAt(account.emailVerifiedAt());
        entity.setPasswordHash(account.passwordHash());
        entity.setStatus(account.status());
        entity.setFailedLoginAttempts(account.failedLoginAttempts());
        entity.setLockedUntil(account.lockedUntil());
        entity.setLastLoginAt(account.lastLoginAt());
        entity.setPlatformRole(account.platformRole());
        entity.setLocale(account.locale().toLanguageTag());
        // Flush: la auditoría (SQL directo con FK a la cuenta) se inserta justo después.
        jpaRepository.saveAndFlush(entity);
    }

    @Override
    public void softDelete(UUID accountId) {
        jpaRepository.findById(accountId).ifPresent(entity -> {
            entity.markDeleted(clock.instant(), CurrentAccount.id().orElse(accountId));
            jpaRepository.saveAndFlush(entity);
        });
    }

    private static Account toDomain(AccountJpaEntity entity) {
        return Account.restore(entity.getId(), entity.getType(), new EmailAddress(entity.getEmail()),
                entity.getEmailVerifiedAt(), entity.getPasswordHash(), entity.getStatus(),
                entity.getFailedLoginAttempts(), entity.getLockedUntil(), entity.getLastLoginAt(),
                entity.getPlatformRole(), Locale.forLanguageTag(entity.getLocale()));
    }
}
