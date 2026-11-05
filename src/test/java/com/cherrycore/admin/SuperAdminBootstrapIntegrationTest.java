package com.cherrycore.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.CustomerFixtures;
import com.cherrycore.support.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "cherrycore.bootstrap.super-admin-email=" + SuperAdminBootstrapIntegrationTest.EMAIL)
class SuperAdminBootstrapIntegrationTest {

    static final String EMAIL = "founder@cherrycore.test";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Test
    void firstSuperAdminIsCreatedAndReceivesAPasswordLink() {
        String role = jdbc.sql("SELECT platform_role FROM accounts WHERE email = ? AND type = 'STAFF'")
                .param(EMAIL).query(String.class).single();

        assertThat(role).isEqualTo("SUPER_ADMIN");
        assertThat(mailbox.awaitToken(EMAIL, CustomerFixtures.RESET_SUBJECT_ES)).isNotBlank();
    }
}
