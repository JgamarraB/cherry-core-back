package com.cherrycore.shared.infrastructure.mail;

import java.nio.charset.StandardCharsets;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component
class SmtpEmailSender implements EmailSender {

    private static final boolean MULTIPART = true;
    private static final boolean IS_HTML = true;

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, CherryCoreProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.mail().from();
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, MULTIPART, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mime);
        } catch (MessagingException invalidMessage) {
            throw new IllegalStateException(invalidMessage);
        }
    }
}
