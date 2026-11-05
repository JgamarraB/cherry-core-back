package com.cherrycore.shared.domain.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Generador de UUID versión 7 (RFC 9562).
 *
 * <p>Los primeros 48 bits son la marca de tiempo en milisegundos, así que los
 * ids se ordenan por fecha de creación (los índices de Postgres funcionan
 * mucho mejor que con UUID aleatorios). El resto es aleatorio criptográfico,
 * por lo que no se pueden adivinar.</p>
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int TIMESTAMP_SHIFT = 16;
    private static final long VERSION_7 = 0x7000L;
    private static final long RAND_A_MASK = 0x0FFFL;
    private static final long VARIANT_BITS = 0x8000000000000000L;
    private static final long RAND_B_MASK = 0x3FFFFFFFFFFFFFFFL;

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(Clock.systemUTC());
    }

    public static UUID generate(Clock clock) {
        long timestamp = clock.millis();
        long mostSignificant = (timestamp << TIMESTAMP_SHIFT) | VERSION_7 | (RANDOM.nextLong() & RAND_A_MASK);
        long leastSignificant = VARIANT_BITS | (RANDOM.nextLong() & RAND_B_MASK);
        return new UUID(mostSignificant, leastSignificant);
    }
}
