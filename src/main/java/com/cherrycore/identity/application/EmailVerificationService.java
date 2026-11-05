package com.cherrycore.identity.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.application.port.OneTimeTokenRepository;
import com.cherrycore.identity.application.port.SecureTokenService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.domain.OneTimeToken;
import com.cherrycore.identity.domain.OneTimeTokenPurpose;
import com.cherrycore.shared.domain.error.DomainException;

@Service
public class EmailVerificationService {

    private final OneTimeTokenRepository tokenRepository;
    private final SecureTokenService tokenService;
    private final AccountRepository accountRepository;
    private final OneTimeTokenIssuer tokenIssuer;
    private final IdentityNotifier notifier;
    private final AuthEventLogger eventLogger;
    private final IdentitySettings settings;
    private final Clock clock;

    public EmailVerificationService(OneTimeTokenRepository tokenRepository, SecureTokenService tokenService,
            AccountRepository accountRepository, OneTimeTokenIssuer tokenIssuer, IdentityNotifier notifier,
            AuthEventLogger eventLogger, IdentitySettings settings, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.accountRepository = accountRepository;
        this.tokenIssuer = tokenIssuer;
        this.notifier = notifier;
        this.eventLogger = eventLogger;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public void confirm(String rawToken, RequestContext context) {
        Instant now = clock.instant();
        OneTimeToken token = tokenRepository.findByHashForUpdate(tokenService.hash(rawToken))
                .filter(found -> found.isUsableFor(OneTimeTokenPurpose.EMAIL_VERIFICATION, now))
                .orElseThrow(() -> new DomainException(IdentityErrorCode.INVALID_OR_EXPIRED_TOKEN));

        Account account = accountRepository.findById(token.accountId())
                .orElseThrow(() -> new DomainException(IdentityErrorCode.INVALID_OR_EXPIRED_TOKEN));

        token.markUsed(now);
        tokenRepository.save(token);
        account.verifyEmail(now);
        accountRepository.save(account);
        eventLogger.log(AuthEventType.EMAIL_VERIFIED, account.id(), context);
    }

    /**
     * Reenvía el email de verificación. No dice nunca si el email existe.
     */
    @Transactional
    public void resend(AccountType type, String rawEmail) {
        EmailAddress email;
        try {
            email = EmailAddress.of(rawEmail);
        } catch (DomainException invalidEmail) {
            return;
        }
        accountRepository.findByTypeAndEmail(type, email)
                .filter(Account::isPendingVerification)
                .ifPresent(account -> notifier.emailVerificationRequested(account,
                        tokenIssuer.issue(account, OneTimeTokenPurpose.EMAIL_VERIFICATION,
                                settings.emailVerificationTtl())));
    }
}
