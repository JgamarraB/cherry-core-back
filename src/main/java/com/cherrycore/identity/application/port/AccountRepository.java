package com.cherrycore.identity.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.EmailAddress;

public interface AccountRepository {

    Optional<Account> findById(UUID id);

    /**
     * Bloquea la fila: los contadores de intentos no se pisan con peticiones simultáneas.
     */
    Optional<Account> findByIdForUpdate(UUID id);

    Optional<Account> findByTypeAndEmail(AccountType type, EmailAddress email);

    /**
     * Igual que {@link #findByTypeAndEmail} pero bloquea la fila hasta el fin
     * de la transacción. Evita que peticiones simultáneas de login se salten
     * el contador de intentos fallidos.
     */
    Optional<Account> findByTypeAndEmailForUpdate(AccountType type, EmailAddress email);

    boolean existsByTypeAndEmail(AccountType type, EmailAddress email);

    List<Account> findSuperAdmins();

    void save(Account account);

    /**
     * Borrado lógico (deleted_at). La cuenta deja de aparecer en las consultas.
     */
    void softDelete(UUID accountId);
}
