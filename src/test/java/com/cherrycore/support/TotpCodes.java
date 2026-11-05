package com.cherrycore.support;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

import com.cherrycore.identity.domain.Totp;

/**
 * Simula la app autenticadora del móvil en los tests.
 */
public final class TotpCodes {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpCodes() {
    }

    /**
     * Código del intervalo actual + {@code offsetSteps} (para no repetir uno ya usado).
     */
    public static String code(String base32Secret, int offsetSteps) {
        long step = Instant.now().getEpochSecond() / Totp.STEP_SECONDS + offsetSteps;
        return Totp.codeAt(decode(base32Secret), step);
    }

    static byte[] decode(String base32) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : base32.toCharArray()) {
            buffer = (buffer << 5) | ALPHABET.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
