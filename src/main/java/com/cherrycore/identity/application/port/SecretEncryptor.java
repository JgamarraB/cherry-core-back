package com.cherrycore.identity.application.port;

/**
 * Cifra secretos que hay que poder recuperar (el del MFA). Si alguien roba
 * la base de datos, sin la clave no le sirven.
 */
public interface SecretEncryptor {

    /** Cifra con la clave actual. */
    String encrypt(byte[] plain);

    /** Descifra con la clave con la que se cifró (la actual o la anterior, durante una rotación). */
    byte[] decrypt(String encrypted);

    /**
     * @return {@code false} si se cifró con una clave que no es la actual: conviene
     *         volver a cifrarlo (así la clave anterior se puede retirar)
     */
    boolean isCurrent(String encrypted);
}
