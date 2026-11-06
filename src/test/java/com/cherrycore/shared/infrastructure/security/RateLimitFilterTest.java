package com.cherrycore.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(
            new CherryCoreProperties.RateLimit(true, 10, 60, Duration.ofMinutes(1)), null);

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

    /*
     * Cada invitación envía un email con el nombre de la empresa: sin límite,
     * una cuenta de backoffice podría usar CherryCore para enviar spam.
     */
    @Test
    void companyInvitationsAreLimitedButOtherCompanyCallsAreNot() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/company/invitations")))
                .isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/company/invitations")))
                .isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("PUT", "/api/company")))
                .isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/auth/customers/login")))
                .isFalse();
    }
}
