package com.cherrycore.identity.application.port;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);

    /**
     * Hace el mismo trabajo que {@link #matches} contra un hash ficticio.
     * Se usa cuando el email no existe, para que la respuesta tarde lo mismo
     * y no se pueda deducir si una cuenta existe midiendo tiempos.
     */
    void simulateMatch(String rawPassword);
}
