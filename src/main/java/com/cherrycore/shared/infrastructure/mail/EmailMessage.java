package com.cherrycore.shared.infrastructure.mail;

/**
 * Email de texto plano ya compuesto y traducido.
 */
public record EmailMessage(String to, String subject, String body) {
}
