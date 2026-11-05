package com.cherrycore.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.awaitility.Awaitility;

import com.cherrycore.shared.infrastructure.mail.EmailMessage;
import com.cherrycore.shared.infrastructure.mail.EmailSender;

/**
 * Buzón falso para tests: guarda los emails en vez de enviarlos.
 * Como el envío es asíncrono, los métodos de lectura esperan un poco.
 */
public class CapturingEmailSender implements EmailSender {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_\\-]+)");

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public void clear() {
        sent.clear();
    }

    public List<EmailMessage> sentTo(String to) {
        return sent.stream().filter(message -> message.to().equals(to)).toList();
    }

    public EmailMessage awaitEmail(String to, String subject) {
        return Awaitility.await().atMost(WAIT).until(
                () -> sentTo(to).stream().filter(message -> message.subject().equals(subject)).findFirst(),
                java.util.Optional::isPresent).orElseThrow();
    }

    public String awaitToken(String to, String subject) {
        Matcher matcher = TOKEN_IN_LINK.matcher(awaitEmail(to, subject).body());
        if (!matcher.find()) {
            throw new AssertionError("No token link in email: " + subject);
        }
        return matcher.group(1);
    }
}
