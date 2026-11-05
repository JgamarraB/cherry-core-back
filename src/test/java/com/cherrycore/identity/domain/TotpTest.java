package com.cherrycore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

class TotpTest {

    /* Vector de prueba oficial del RFC 6238 (SHA-1): en T=59s el código de 8 dígitos es 94287082. */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238TestVector() {
        assertThat(Totp.codeAt(RFC_SECRET, 59 / Totp.STEP_SECONDS)).isEqualTo("287082");
    }

    @Test
    void acceptsOneStepOfClockDriftButNotMore() {
        Instant now = Instant.ofEpochSecond(1_000_000);
        long step = now.getEpochSecond() / Totp.STEP_SECONDS;

        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 1), now, OptionalLong.empty())).isPresent();
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 1), now, OptionalLong.empty())).isPresent();
        assertThat(Totp.verify(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 2), now, OptionalLong.empty())).isEmpty();
    }

    @Test
    void aCodeCannotBeReused() {
        Instant now = Instant.ofEpochSecond(1_000_000);
        long step = now.getEpochSecond() / Totp.STEP_SECONDS;
        String code = Totp.codeAt(RFC_SECRET, step);

        OptionalLong used = Totp.verify(RFC_SECRET, code, now, OptionalLong.empty());
        assertThat(used).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, code, now, used)).isEmpty();
    }

    @Test
    void rejectsMalformedCodes() {
        Instant now = Instant.now();
        assertThat(Totp.verify(RFC_SECRET, "12345", now, OptionalLong.empty())).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "abcdef", now, OptionalLong.empty())).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, OptionalLong.empty())).isEmpty();
    }

    @Test
    void base32MatchesRfc4648() {
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
    }
}
