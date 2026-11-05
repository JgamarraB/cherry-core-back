package com.cherrycore.identity.application.port;

import java.util.Optional;
import java.util.UUID;

import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.EmailAddress;

public interface AccountRepository {

    Optional<Account> findById(UUID id);

    Optional<Account> findByTypeAndEmail(AccountType type, EmailAddress email);

    /**
     * Igual que {@link #findByTypeAndEmail} pero bloquea la fila hasta el fin
     * de la transacción. Evita que peticiones simultáneas de login se salten
     * el contador de intentos fallidos.
     */
    Optional<Account> findByTypeAndEmailForUpdate(AccountType type, EmailAddress email);

    boolean existsByTypeAndEmail(AccountType type, EmailAddress email);

    void save(Account account);
}
