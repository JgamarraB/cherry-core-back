package com.cherrycore.identity.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos de un solo uso basados en tiempo (TOTP, RFC 6238): los de Google
 * Authenticator, Authy, 1Password... 6 dígitos que cambian cada 30 segundos.
 */
public final class Totp {

    public static final int SECRET_BYTES = 20;
    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    /* Se acepta el código anterior y el siguiente por desfase de reloj del móvil. */
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};
    private static final int DYNAMIC_OFFSET_MASK = 0x0F;
    private static final int POSITIVE_MASK = 0x7FFFFFFF;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /**
     * @param lastUsedStep último intervalo ya usado (o vacío): un código no se
     *                     puede usar dos veces (protección contra replay)
     * @return el intervalo del código válido, o vacío si no es válido
     */
    public static OptionalLong verify(byte[] secret, String code, Instant now, OptionalLong lastUsedStep) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        long currentStep = now.getEpochSecond() / STEP_SECONDS;
        for (long step = currentStep - ALLOWED_DRIFT_STEPS; step <= currentStep + ALLOWED_DRIFT_STEPS; step++) {
            boolean alreadyUsed = lastUsedStep.isPresent() && step <= lastUsedStep.getAsLong();
            if (!alreadyUsed && constantTimeEquals(codeAt(secret, step), code)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public static String codeAt(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & DYNAMIC_OFFSET_MASK;
            int binary = ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & POSITIVE_MASK;
            return String.format("%0" + DIGITS + "d", binary % POWERS_OF_TEN[DIGITS]);
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
