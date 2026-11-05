package com.cherrycore.shared.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.cherrycore.identity.application.port.RecoveryCodeHasher;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * HMAC-SHA256 con la clave {@code cherrycore.security.hash-key}. Sin ella la
 * aplicación no arranca (no hay valor por defecto fuera de local y tests).
 */
@Component
class HmacRecoveryCodeHasher implements RecoveryCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    HmacRecoveryCodeHasher(CherryCoreProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.security().hashKey());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("cherrycore.security.hash-key must be at least 32 bytes in Base64");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    @Override
    public String hash(String normalizedCode) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalizedCode.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
