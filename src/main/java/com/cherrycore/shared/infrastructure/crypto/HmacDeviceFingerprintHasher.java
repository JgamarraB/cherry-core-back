package com.cherrycore.shared.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.cherrycore.identity.application.port.DeviceFingerprintHasher;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * HMAC-SHA256 de "device:{cuenta}:{id de la cookie}" con la clave
 * {@code cherrycore.security.hash-key}. El prefijo separa estos hashes de los
 * de los códigos de recuperación aunque compartan clave.
 */
@Component
class HmacDeviceFingerprintHasher implements DeviceFingerprintHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN = "device:";

    private final SecretKeySpec key;

    HmacDeviceFingerprintHasher(CherryCoreProperties properties) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.security().hashKey()), ALGORITHM);
    }

    @Override
    public String hash(UUID accountId, String deviceId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            String fingerprint = DOMAIN + accountId + ":" + deviceId;
            return HexFormat.of().formatHex(mac.doFinal(fingerprint.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
