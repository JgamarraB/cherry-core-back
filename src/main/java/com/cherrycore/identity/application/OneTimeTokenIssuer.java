package com.cherrycore.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.cherrycore.identity.application.port.OneTimeTokenRepository;
import com.cherrycore.identity.application.port.SecureTokenService;
import com.cherrycore.identity.application.port.SecureTokenService.GeneratedToken;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.OneTimeToken;
import com.cherrycore.identity.domain.OneTimeTokenPurpose;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Emite un token de un solo uso y anula los anteriores del mismo tipo.
 */
@Component
public class OneTimeTokenIssuer {

    private final OneTimeTokenRepository tokenRepository;
    private final SecureTokenService tokenService;
    private final Clock clock;

    public OneTimeTokenIssuer(OneTimeTokenRepository tokenRepository, SecureTokenService tokenService, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    /**
     * @return el token en claro, para el enlace del email
     */
    public String issue(Account account, OneTimeTokenPurpose purpose, Duration ttl) {
        return issue(account, purpose, ttl, null);
    }

    public String issue(Account account, OneTimeTokenPurpose purpose, Duration ttl, String payload) {
        Instant now = clock.instant();
        tokenRepository.invalidatePending(account.id(), purpose, now);
        GeneratedToken generated = tokenService.generate();
        tokenRepository.save(new OneTimeToken(UuidV7.generate(clock), account.id(), purpose,
                generated.hash(), now.plus(ttl), null, payload));
        return generated.rawValue();
    }
}
