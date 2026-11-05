package com.cherrycore.identity.application.port;

import java.util.Locale;

import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.EmailAddress;

/**
 * Avisos al usuario (por email). Se envían de forma asíncrona tras confirmar
 * la transacción, para no ralentizar ni delatar con el tiempo de respuesta.
 */
public interface IdentityNotifier {

    void emailVerificationRequested(Account account, String rawToken);

    void passwordResetRequested(Account account, String rawToken);

    void registrationAttemptedOnExistingAccount(Account account);

    void accountLocked(Account account);

    void passwordChanged(Account account);

    /**
     * Enlace de confirmación al email NUEVO.
     */
    void emailChangeRequested(Account account, EmailAddress newEmail, String rawToken);

    /**
     * Aviso al email ANTERIOR de que se ha cambiado.
     */
    void emailChanged(EmailAddress previousEmail, Locale locale);

    /**
     * Aviso al email ACTUAL de que se ha pedido cambiarlo (sin enlace de
     * confirmación): si no ha sido su dueño, aún está a tiempo de reaccionar.
     */
    void emailChangeRequestedNotice(Account account, EmailAddress newEmail);

    /**
     * Confirmación de borrado al email que tenía la cuenta.
     */
    void accountDeleted(EmailAddress email, Locale locale);
}
