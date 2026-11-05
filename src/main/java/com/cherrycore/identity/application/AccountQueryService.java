package com.cherrycore.identity.application;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Consultas y cambios sencillos sobre una cuenta, para otros módulos y para
 * la emisión de tokens.
 */
@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;
    private final IdentitySettings settings;

    public AccountQueryService(AccountRepository accountRepository, IdentitySettings settings) {
        this.accountRepository = accountRepository;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Optional<Account> findActive(UUID accountId) {
        return accountRepository.findById(accountId).filter(Account::isActive);
    }

    @Transactional(readOnly = true)
    public Account getActive(UUID accountId) {
        return findActive(accountId).orElseThrow(() -> new DomainException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<Account> findById(UUID accountId) {
        return accountRepository.findById(accountId);
    }

    @Transactional(readOnly = true)
    public Optional<Account> findByTypeAndEmail(AccountType type, EmailAddress email) {
        return accountRepository.findByTypeAndEmail(type, email);
    }

    @Transactional(readOnly = true)
    public List<Account> superAdmins() {
        return accountRepository.findSuperAdmins().stream().filter(Account::isActive).toList();
    }

    @Transactional
    public Account changeLocale(UUID accountId, Locale requested) {
        Account account = getActive(accountId);
        boolean supported = settings.supportedLocales().stream()
                .anyMatch(locale -> locale.getLanguage().equals(requested.getLanguage()));
        if (!supported) {
            throw new DomainException(IdentityErrorCode.UNSUPPORTED_LOCALE);
        }
        account.changeLocale(settings.resolveLocale(requested));
        accountRepository.save(account);
        return account;
    }
}
