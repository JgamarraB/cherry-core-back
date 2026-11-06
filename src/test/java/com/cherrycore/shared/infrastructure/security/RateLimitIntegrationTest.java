package com.cherrycore.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CustomerFixtures;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;

@IntegrationTest
@TestPropertySource(properties = {
        "cherrycore.security.rate-limit.enabled=true",
        "cherrycore.security.rate-limit.capacity=3",
        "cherrycore.security.rate-limit.read-capacity=2"
})
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tooManyLoginAttemptsFromTheSameIpAreThrottled() throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(browser.login(CustomerFixtures.uniqueEmail(), "x").getResponse().getStatus()).isEqualTo(401);
        }

        MvcResult throttled = browser.login(CustomerFixtures.uniqueEmail(), "x");

        assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
        assertThat(throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(JsonBodies.<String>read(throttled, "$.error.code")).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void appBrandingLookupsAreThrottledPerIpWhateverTheClientId() throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc);
        assertThat(browser.get("/api/auth/apps/unknown-one").getResponse().getStatus()).isEqualTo(404);
        assertThat(browser.get("/api/auth/apps/unknown-two").getResponse().getStatus()).isEqualTo(404);

        MvcResult throttled = browser.get("/api/auth/apps/unknown-three");

        assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
        assertThat(JsonBodies.<String>read(throttled, "$.error.code")).isEqualTo("TOO_MANY_REQUESTS");
    }
}
