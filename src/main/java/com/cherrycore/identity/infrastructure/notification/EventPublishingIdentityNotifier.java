package com.cherrycore.identity.infrastructure.notification;

import java.net.URI;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * Convierte cada aviso en un evento. El envío real lo hace
 * {@link IdentityEmailListener} cuando la transacción se ha confirmado
 * (si la operación falla, no se envía ningún email).
 */
@Component
class EventPublishingIdentityNotifier implements IdentityNotifier {

    static final String TOKEN_PARAM = "token";

    private final ApplicationEventPublisher publisher;
    private final CherryCoreProperties.Frontend frontend;

    EventPublishingIdentityNotifier(ApplicationEventPublisher publisher, CherryCoreProperties properties) {
        this.publisher = publisher;
        this.frontend = properties.frontend();
    }

    @Override
    public void emailVerificationRequested(Account account, String rawToken) {
        publish(IdentityEmailType.EMAIL_VERIFICATION, account, linkWithToken(frontend.emailVerificationUrl(), rawToken));
    }

    @Override
    public void passwordResetRequested(Account account, String rawToken) {
        publish(IdentityEmailType.PASSWORD_RESET, account, linkWithToken(frontend.passwordResetUrl(), rawToken));
    }

    @Override
    public void registrationAttemptedOnExistingAccount(Account account) {
        publish(IdentityEmailType.REGISTRATION_ON_EXISTING_ACCOUNT, account, frontend.loginUrl().toString());
    }

    @Override
    public void accountLocked(Account account) {
        publish(IdentityEmailType.ACCOUNT_LOCKED, account, frontend.loginUrl().toString());
    }

    @Override
    public void passwordChanged(Account account) {
        publish(IdentityEmailType.PASSWORD_CHANGED, account, frontend.loginUrl().toString());
    }

    private void publish(IdentityEmailType type, Account account, String link) {
        publisher.publishEvent(new IdentityEmailRequested(type, account.email().value(), account.locale(), link));
    }

    private static String linkWithToken(URI baseUrl, String rawToken) {
        return UriComponentsBuilder.fromUri(baseUrl).queryParam(TOKEN_PARAM, rawToken).build().toUriString();
    }
}
