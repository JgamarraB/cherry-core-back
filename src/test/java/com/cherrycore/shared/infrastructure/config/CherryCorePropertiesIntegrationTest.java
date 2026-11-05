package com.cherrycore.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cherrycore.support.IntegrationTest;

@IntegrationTest
class CherryCorePropertiesIntegrationTest {

    @Autowired
    private CherryCoreProperties properties;

    @Test
    void bindsTokenLifetimesFromApplicationYaml() {
        CherryCoreProperties.Tokens tokens = properties.security().tokens();

        assertThat(tokens.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(tokens.customerRefreshTokenTtl()).isEqualTo(Duration.ofDays(30));
        assertThat(tokens.staffRefreshTokenTtl()).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void bindsCorsOriginsWithoutWildcard() {
        assertThat(properties.cors().allowedOrigins()).isNotEmpty().doesNotContain("*");
    }
}
