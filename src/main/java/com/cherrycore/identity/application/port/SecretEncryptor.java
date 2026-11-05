package com.cherrycore.identity.application.port;

/**
 * Cifra secretos que hay que poder recuperar (el del MFA). Si alguien roba
 * la base de datos, sin la clave no le sirven.
 */
public interface SecretEncryptor {

    String encrypt(byte[] plain);

    byte[] decrypt(String encrypted);
}
