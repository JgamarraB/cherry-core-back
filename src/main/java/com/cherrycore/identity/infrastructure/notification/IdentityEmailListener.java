package com.cherrycore.identity.infrastructure.notification;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.mail.EmailMessage;
import com.cherrycore.shared.infrastructure.mail.EmailSender;

/**
 * Compone el email en el idioma de la cuenta y lo envía en segundo plano.
 *
 * <p>Es asíncrono por seguridad además de por rendimiento: así "olvidé mi
 * contraseña" tarda lo mismo exista o no el email.</p>
 */
@Component
class IdentityEmailListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityEmailListener.class);
    private static final String LOG_SEND_FAILED = "Could not send identity email [{}]";

    private final MessageSource messageSource;
    private final EmailSender emailSender;
    private final CherryCoreProperties.OneTimeTokens tokenSettings;

    IdentityEmailListener(MessageSource messageSource, EmailSender emailSender, CherryCoreProperties properties) {
        this.messageSource = messageSource;
        this.emailSender = emailSender;
        this.tokenSettings = properties.security().oneTimeTokens();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(IdentityEmailRequested request) {
        try {
            emailSender.send(compose(request));
        } catch (RuntimeException sendFailure) {
            // Nunca se registra el enlace (lleva el token) ni el email del usuario.
            LOGGER.error(LOG_SEND_FAILED, request.type(), sendFailure);
        }
    }

    EmailMessage compose(IdentityEmailRequested request) {
        Object[] args = {request.link(), validityOf(request.type())};
        String subject = messageSource.getMessage(request.type().subjectKey(), null, request.locale());
        String body = messageSource.getMessage(request.type().bodyKey(), args, request.locale());
        return new EmailMessage(request.to(), subject, body);
    }

    /*
     * Validez del enlace para mostrarla en el texto: horas para la verificación,
     * minutos para la recuperación de contraseña.
     */
    private long validityOf(IdentityEmailType type) {
        return switch (type) {
            case EMAIL_VERIFICATION -> tokenSettings.emailVerificationTtl().toHours();
            case PASSWORD_RESET -> tokenSettings.passwordResetTtl().toMinutes();
            default -> Duration.ZERO.toMinutes();
        };
    }
}
