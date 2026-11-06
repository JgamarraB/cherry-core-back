package com.cherrycore.identity.infrastructure.notification;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.DeviceDescription;
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
    private final MessageSource messages;

    EventPublishingIdentityNotifier(ApplicationEventPublisher publisher, CherryCoreProperties properties,
            MessageSource messages) {
        this.publisher = publisher;
        this.messages = messages;
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

    /*
     * Fecha en UTC (el email no sabe la zona de quien lo lee) y dispositivo
     * aproximado ("Chrome en macOS"). Sin enlaces con token: el botón lleva a
     * la actividad de la cuenta, que pide iniciar sesión.
     */
    @Override
    public void newDeviceLogin(Account account, Instant at, RequestContext context) {
        Locale locale = account.locale();
        String when = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale).withZone(ZoneOffset.UTC).format(at);
        String ip = context.ip() == null ? text("email.new-device.unknown", locale) : context.ip();
        publish(IdentityEmailType.NEW_DEVICE_LOGIN, account, account.email().value(),
                frontend.accountActivityUrl().toString(), when, deviceLabel(context.userAgent(), locale), ip);
    }

    private String deviceLabel(String userAgent, Locale locale) {
        DeviceDescription device = DeviceDescription.fromUserAgent(userAgent);
        if (device.browser() != null && device.os() != null) {
            return messages.getMessage("email.new-device.device", new Object[] {device.browser(), device.os()},
                    locale);
        }
        return device.isKnown() ? (device.browser() != null ? device.browser() : device.os())
                : text("email.new-device.unknown", locale);
    }

    private String text(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }

    private void publish(IdentityEmailType type, Account account, String to, String actionUrl, Object... args) {
        publisher.publishEvent(new EmailRequested(type.key(), to, account.locale(), null, actionUrl, List.of(args)));
    }

    static String linkWithToken(URI baseUrl, String rawToken) {
        return UriComponentsBuilder.fromUri(baseUrl).queryParam(TOKEN_PARAM, rawToken).build().toUriString();
    }
}
