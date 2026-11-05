package com.cherrycore.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cherrycore.shared.domain.id.UuidV7;

class UuidV7Test {

    @Test
    void hasVersion7AndRfcVariant() {
        UUID id = UuidV7.generate();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void sortsByCreationTime() {
        UUID earlier = UuidV7.generate(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        UUID later = UuidV7.generate(Clock.fixed(Instant.parse("2026-01-01T00:00:01Z"), ZoneOffset.UTC));
        assertThat(earlier.toString()).isLessThan(later.toString());
    }
}
