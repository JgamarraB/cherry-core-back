package com.cherrycore.shared.infrastructure.mail;

/**
 * Envío de emails. Cambiar de proveedor (Mailpit, Brevo, SES...) es cambiar
 * la configuración SMTP o añadir otra implementación de esta interfaz.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
