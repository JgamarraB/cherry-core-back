package com.cherrycore.identity.domain;

/**
 * Codificación Base32 (RFC 4648): el formato en el que las apps
 * autenticadoras esperan el secreto.
 */
public final class Base32 {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int BITS_PER_CHAR = 5;
    private static final int BITS_PER_BYTE = 8;
    private static final int CHAR_MASK = 0x1F;
    private static final int BYTE_MASK = 0xFF;

    private Base32() {
    }

    public static String encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << BITS_PER_BYTE) | (value & BYTE_MASK);
            bitsLeft += BITS_PER_BYTE;
            while (bitsLeft >= BITS_PER_CHAR) {
                result.append(ALPHABET[(buffer >> (bitsLeft - BITS_PER_CHAR)) & CHAR_MASK]);
                bitsLeft -= BITS_PER_CHAR;
            }
        }
        if (bitsLeft > 0) {
            result.append(ALPHABET[(buffer << (BITS_PER_CHAR - bitsLeft)) & CHAR_MASK]);
        }
        return result.toString();
    }
}
