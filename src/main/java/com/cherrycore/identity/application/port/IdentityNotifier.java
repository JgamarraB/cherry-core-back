package com.cherrycore.identity.application.port;

import com.cherrycore.identity.domain.Account;

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
}
