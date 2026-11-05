package com.cherrycore.shared.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * Compone (idioma + marca + HTML) y envía los emails en segundo plano.
 *
 * <p>Asíncrono también por seguridad: así "olvidé mi contraseña" tarda lo
 * mismo exista o no el email.</p>
 */
@Component
public class EmailDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailDispatcher.class);
    private static final String LOG_SEND_FAILED = "Could not send email [{}]";

    private static final String SUBJECT_SUFFIX = ".subject";
    private static final String BODY_SUFFIX = ".body";
    private static final String ACTION_SUFFIX = ".action";

    private final MessageSource messageSource;
    private final EmailRenderer renderer;
    private final EmailSender sender;
    private final EmailBranding platformBranding;

    EmailDispatcher(MessageSource messageSource, EmailRenderer renderer, EmailSender sender,
            CherryCoreProperties properties) {
        this.messageSource = messageSource;
        this.renderer = renderer;
        this.sender = sender;
        this.platformBranding = new EmailBranding(properties.branding().platformName(), null);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(EmailRequested request) {
        try {
            sender.send(compose(request));
        } catch (RuntimeException sendFailure) {
            // Nunca se registra el enlace (puede llevar un token) ni el destinatario.
            LOGGER.error(LOG_SEND_FAILED, request.templateKey(), sendFailure);
        }
    }

    EmailMessage compose(EmailRequested request) {
        String key = request.templateKey();
        String subject = messageSource.getMessage(key + SUBJECT_SUFFIX, null, request.locale());
        String body = messageSource.getMessage(key + BODY_SUFFIX, request.bodyArgs().toArray(), request.locale());
        String action = request.actionUrl() == null
                ? null
                : messageSource.getMessage(key + ACTION_SUFFIX, null, request.locale());
        EmailBranding branding = request.branding() == null ? platformBranding : request.branding();
        return renderer.render(new EmailContent(request.to(), request.locale(), branding, subject, body,
                request.actionUrl(), action));
    }
}
