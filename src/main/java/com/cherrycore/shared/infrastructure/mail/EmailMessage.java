package com.cherrycore.shared.infrastructure.mail;

/**
 * Email ya compuesto y traducido, en texto y en HTML.
 */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {
}
