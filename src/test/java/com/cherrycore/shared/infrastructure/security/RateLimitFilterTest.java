package com.cherrycore.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitFilterTest {

    @Test
    void groupsIpv6AddressesByTheirSlash64() {
        assertThat(RateLimitFilter.clientKey("2001:db8:1:2:aaaa::1"))
                .isEqualTo(RateLimitFilter.clientKey("2001:db8:1:2:bbbb::9"))
                .isNotEqualTo(RateLimitFilter.clientKey("2001:db8:1:3::1"));
    }

    @Test
    void keepsIpv4AddressesAsTheyAre() {
        assertThat(RateLimitFilter.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
    }
}
