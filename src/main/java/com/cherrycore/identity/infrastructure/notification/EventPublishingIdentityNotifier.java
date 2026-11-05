package com.cherrycore.identity.infrastructure.notification;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.mail.EmailRequested;

/**
 * Convierte cada aviso en un email (evento procesado al confirmar la transacción).
 * Los emails de cuenta llevan la marca de la plataforma.
 */
@Component
class EventPublishingIdentityNotifier implements IdentityNotifier {

    static final String TOKEN_PARAM = "token";

    private final ApplicationEventPublisher publisher;
    private final CherryCoreProperties.Frontend frontend;
    private final CherryCoreProperties.OneTimeTokens tokenSettings;

    EventPublishingIdentityNotifier(ApplicationEventPublisher publisher, CherryCoreProperties properties) {
        this.publisher = publisher;
        this.frontend = properties.frontend();
        this.tokenSettings = properties.security().oneTimeTokens();
    }

    @Override
    public void emailVerificationRequested(Account account, String rawToken) {
        publish(IdentityEmailType.EMAIL_VERIFICATION, account, account.email().value(),
                linkWithToken(frontend.emailVerificationUrl(), rawToken),
                tokenSettings.emailVerificationTtl().toHours());
    }

    @Override
    public void passwordResetRequested(Account account, String rawToken) {
        publish(IdentityEmailType.PASSWORD_RESET, account, account.email().value(),
                linkWithToken(frontend.passwordResetUrl(), rawToken),
                tokenSettings.passwordResetTtl().toMinutes());
    }

    @Override
    public void registrationAttemptedOnExistingAccount(Account account) {
        publish(IdentityEmailType.REGISTRATION_ON_EXISTING_ACCOUNT, account, account.email().value(),
                frontend.loginUrl().toString());
    }

    @Override
    public void accountLocked(Account account) {
        publish(IdentityEmailType.ACCOUNT_LOCKED, account, account.email().value(), frontend.loginUrl().toString());
    }

    @Override
    public void passwordChanged(Account account) {
        publish(IdentityEmailType.PASSWORD_CHANGED, account, account.email().value(), frontend.loginUrl().toString());
    }

    @Override
    public void emailChangeRequested(Account account, EmailAddress newEmail, String rawToken) {
        publish(IdentityEmailType.EMAIL_CHANGE, account, newEmail.value(),
                linkWithToken(frontend.emailChangeUrl(), rawToken), tokenSettings.emailVerificationTtl().toHours());
    }

    @Override
    public void emailChanged(EmailAddress previousEmail, Locale locale) {
        publisher.publishEvent(new EmailRequested(IdentityEmailType.EMAIL_CHANGED.key(), previousEmail.value(),
                locale, null, frontend.passwordResetUrl().toString(), List.of()));
    }

    @Override
    public void emailChangeRequestedNotice(Account account, EmailAddress newEmail) {
        publish(IdentityEmailType.EMAIL_CHANGE_REQUESTED, account, account.email().value(),
                frontend.passwordResetUrl().toString(), newEmail.value());
    }

    @Override
    public void accountDeleted(EmailAddress email, Locale locale) {
        publisher.publishEvent(new EmailRequested(IdentityEmailType.ACCOUNT_DELETED.key(), email.value(), locale,
                null, null, List.of()));
    }

    private void publish(IdentityEmailType type, Account account, String to, String actionUrl, Object... args) {
        publisher.publishEvent(new EmailRequested(type.key(), to, account.locale(), null, actionUrl, List.of(args)));
    }

    static String linkWithToken(URI baseUrl, String rawToken) {
        return UriComponentsBuilder.fromUri(baseUrl).queryParam(TOKEN_PARAM, rawToken).build().toUriString();
    }
}
