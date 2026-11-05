package com.cherrycore.identity.domain;

/**
 * Tipo de cuenta. Clientes y backoffice son cuentas independientes
 * (el mismo email puede existir en ambos tipos).
 */
public enum AccountType {
    CUSTOMER,
    STAFF
}
