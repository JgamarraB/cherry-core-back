package com.cherrycore.shared.infrastructure.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cherrycore.identity.application.port.SecretEncryptor;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * Cifrado AES-256-GCM (cifra y además detecta si alguien ha manipulado el dato).
 *
 * <p>Formato guardado: {@code k{idDeClave}:Base64(nonce de 12 bytes + texto cifrado + etiqueta)}.
 * El id dice con qué clave descifrar, así la clave se puede rotar: se configura
 * la nueva como actual y la anterior en {@code previous-encryption-key}; lo
 * antiguo se vuelve a cifrar con la nueva cuando se usa.</p>
 *
 * <p>Los datos sin prefijo (anteriores al id de clave) se descifran probando la
 * clave actual y, si no vale, la anterior.</p>
 */
@Component
class AesGcmSecretEncryptor implements SecretEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String VERSION_PREFIX = "k";
    private static final char VERSION_SEPARATOR = ':';

    private final String currentId;
    private final SecretKeySpec currentKey;
    /* Por id, la actual primero. */
    private final Map<String, SecretKeySpec> keys = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Autowired
    AesGcmSecretEncryptor(CherryCoreProperties properties) {
        this(properties.security().encryptionKeyId(), properties.security().encryptionKey(),
                properties.security().previousEncryptionKey());
    }

    AesGcmSecretEncryptor(String currentId, String currentKeyBase64,
            CherryCoreProperties.PreviousEncryptionKey previous) {
        this.currentId = currentId;
        this.currentKey = key(currentKeyBase64, "cherrycore.security.encryption-key");
        keys.put(currentId, currentKey);
        if (previous != null) {
            if (previous.id().equals(currentId)) {
                throw new IllegalStateException("previous-encryption-key.id must differ from encryption-key-id");
            }
            keys.put(previous.id(), key(previous.key(), "cherrycore.security.previous-encryption-key.key"));
        }
    }

    @Override
    public String encrypt(byte[] plain) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plain);
            return VERSION_PREFIX + currentId + VERSION_SEPARATOR + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override
    public byte[] decrypt(String encrypted) {
        int separator = encrypted.indexOf(VERSION_SEPARATOR);
        if (separator < 0) {
            return decryptLegacy(encrypted);
        }
        String keyId = encrypted.substring(VERSION_PREFIX.length(), separator);
        SecretKeySpec key = keys.get(keyId);
        if (!encrypted.startsWith(VERSION_PREFIX) || key == null) {
            throw new IllegalStateException("Unknown encryption key id: " + keyId);
        }
        try {
            return decrypt(encrypted.substring(separator + 1), key);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override
    public boolean isCurrent(String encrypted) {
        return encrypted.startsWith(VERSION_PREFIX + currentId + VERSION_SEPARATOR);
    }

    private byte[] decryptLegacy(String encrypted) {
        GeneralSecurityException last = null;
        for (SecretKeySpec key : keys.values()) {
            try {
                return decrypt(encrypted, key);
            } catch (GeneralSecurityException wrongKey) {
                last = wrongKey;
            }
        }
        throw new IllegalStateException(last);
    }

    private static byte[] decrypt(String base64, SecretKeySpec key) throws GeneralSecurityException {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(base64));
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        return cipher.doFinal(cipherText);
    }

    private static SecretKeySpec key(String base64, String property) {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(property + " must be 32 bytes in Base64");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
