package com.cherrycore.identity.domain;

import java.util.UUID;

/**
 * Evento de dominio: se va a borrar una cuenta. Cada módulo (clientes,
 * empresas...) borra o anonimiza sus datos de esa cuenta. Se procesa en la
 * misma transacción: si un módulo lo impide (p. ej. único dueño de una
 * empresa), no se borra nada.
 */
public record AccountDeletionRequested(UUID accountId, AccountType type) {
}
