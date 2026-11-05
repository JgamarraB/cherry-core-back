package com.cherrycore.shared.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.PreviousEncryptionKey;

/**
 * Rotación de la clave que cifra los secretos MFA.
 */
class AesGcmSecretEncryptorTest {

    private static final String OLD_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String NEW_KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final byte[] SECRET = "totp-secret".getBytes(StandardCharsets.US_ASCII);

    @Test
    void newCiphertextsCarryTheKeyId() {
        AesGcmSecretEncryptor encryptor = new AesGcmSecretEncryptor("2026a", NEW_KEY, null);

        String encrypted = encryptor.encrypt(SECRET);

        assertThat(encrypted).startsWith("k2026a:");
        assertThat(encryptor.isCurrent(encrypted)).isTrue();
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(SECRET);
    }

    @Test
    void afterARotationOldCiphertextsStillDecryptAndAreFlaggedForReencryption() {
        String underOldKey = new AesGcmSecretEncryptor("1", OLD_KEY, null).encrypt(SECRET);
        AesGcmSecretEncryptor rotated = new AesGcmSecretEncryptor("2", NEW_KEY,
                new PreviousEncryptionKey("1", OLD_KEY));

        assertThat(rotated.decrypt(underOldKey)).isEqualTo(SECRET);
        assertThat(rotated.isCurrent(underOldKey)).isFalse();
        assertThat(rotated.encrypt(SECRET)).startsWith("k2:");
    }

    @Test
    void unversionedLegacyCiphertextsAreReadWithTheCurrentOrPreviousKey() {
        String legacy = new AesGcmSecretEncryptor("1", OLD_KEY, null).encrypt(SECRET).substring("k1:".length());

        assertThat(new AesGcmSecretEncryptor("1", OLD_KEY, null).decrypt(legacy)).isEqualTo(SECRET);
        AesGcmSecretEncryptor rotated = new AesGcmSecretEncryptor("2", NEW_KEY,
                new PreviousEncryptionKey("1", OLD_KEY));
        assertThat(rotated.decrypt(legacy)).isEqualTo(SECRET);
        assertThat(rotated.isCurrent(legacy)).isFalse();
    }

    @Test
    void unknownKeyIdsAndBadConfigurationFail() {
        String underOldKey = new AesGcmSecretEncryptor("1", OLD_KEY, null).encrypt(SECRET);
        assertThatThrownBy(() -> new AesGcmSecretEncryptor("2", NEW_KEY, null).decrypt(underOldKey))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmSecretEncryptor("1", NEW_KEY, new PreviousEncryptionKey("1", OLD_KEY)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmSecretEncryptor("1", "c2hvcnQ=", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
