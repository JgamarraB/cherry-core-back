package com.cherrycore.shared.infrastructure.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.cherrycore.identity.application.port.SecretEncryptor;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * Cifrado AES-256-GCM (cifra y además detecta si alguien ha manipulado el dato).
 * Formato guardado: Base64(nonce de 12 bytes + texto cifrado + etiqueta).
 */
@Component
class AesGcmSecretEncryptor implements SecretEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    AesGcmSecretEncryptor(CherryCoreProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.security().encryptionKey());
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("cherrycore.security.encryption-key must be 32 bytes in Base64");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    @Override
    public String encrypt(byte[] plain) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plain);
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override
    public byte[] decrypt(String encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encrypted));
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
