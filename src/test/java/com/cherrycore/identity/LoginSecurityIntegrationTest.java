package com.cherrycore.identity;

import static com.cherrycore.support.CustomerFixtures.LOCKED_SUBJECT_ES;
import static com.cherrycore.support.CustomerFixtures.PASSWORD;
import static com.cherrycore.support.CustomerFixtures.registerVerified;
import static com.cherrycore.support.CustomerFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;

@IntegrationTest
class LoginSecurityIntegrationTest {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String SESSION_COOKIE = "CHERRYCORE_SESSION";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private JdbcClient jdbc;

    private BrowserSession browser;

    @BeforeEach
    void setUp() {
        browser = new BrowserSession(mockMvc);
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameError() throws Exception {
        String email = registerVerified(browser, mailbox);

        MvcResult unknown = browser.login(uniqueEmail(), PASSWORD);
        MvcResult wrongPassword = browser.login(email, "Clave-incorrecta-99");
        MvcResult notAnEmail = browser.login("no-es-un-email", PASSWORD);

        for (MvcResult result : new MvcResult[] {unknown, wrongPassword, notAnEmail}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
            assertThat(JsonBodies.<String>read(result, "$.error.code")).isEqualTo("INVALID_CREDENTIALS");
        }
    }

    @Test
    void accountIsLockedAfterRepeatedFailuresEvenWithTheRightPassword() throws Exception {
        String email = registerVerified(browser, mailbox);

        for (int attempt = 0; attempt < MAX_FAILED_ATTEMPTS; attempt++) {
            browser.login(email, "Clave-incorrecta-99");
        }
        MvcResult whileLocked = browser.login(email, PASSWORD);

        assertThat(whileLocked.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonBodies.<String>read(whileLocked, "$.error.code")).isEqualTo("INVALID_CREDENTIALS");
        mailbox.awaitEmail(email, LOCKED_SUBJECT_ES);
        long lockEvents = jdbc.sql("""
                SELECT COUNT(*) FROM auth_events e JOIN accounts a ON a.id = e.account_id
                 WHERE a.email = ? AND e.type = 'ACCOUNT_LOCKED'
                """).param(email).query(Long.class).single();
        assertThat(lockEvents).isEqualTo(1);
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        String email = registerVerified(browser, mailbox);
        BrowserSession attacker = new BrowserSession(mockMvc);

        MvcResult result = attacker.postJsonWithoutCsrf(AuthPaths.CUSTOMER_LOGIN,
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonBodies.<String>read(result, "$.error.code")).isEqualTo("CSRF_TOKEN_INVALID");
    }

    @Test
    void sessionIdChangesOnLoginAndLogoutEndsTheSession() throws Exception {
        String email = registerVerified(browser, mailbox);

        MvcResult login = browser.login(email, PASSWORD);
        assertThat(login.getResponse().getCookie(SESSION_COOKIE)).isNotNull();
        // También llega la cookie del dispositivo: se busca la de sesión entre todas.
        assertThat(login.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith(SESSION_COOKIE + "=")).findFirst().orElseThrow())
                .contains("HttpOnly")
                .contains("SameSite=Lax");

        browser.postJson(AuthPaths.LOGOUT, "{}");
        MvcResult session = browser.get("http://localhost" + AuthPaths.BASE + AuthPaths.SESSION,
                org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(JsonBodies.<Boolean>read(session, "$.data.authenticated")).isFalse();
    }

    @Test
    void failedAndSuccessfulLoginsAreAudited() throws Exception {
        String email = registerVerified(browser, mailbox);
        browser.login(email, "Clave-incorrecta-99");
        browser.login(email, PASSWORD);

        var types = jdbc.sql("""
                SELECT e.type FROM auth_events e JOIN accounts a ON a.id = e.account_id
                 WHERE a.email = ? ORDER BY e.occurred_at
                """).param(email).query(String.class).list();
        assertThat(types).contains("ACCOUNT_REGISTERED", "EMAIL_VERIFIED", "LOGIN_FAILED", "LOGIN_SUCCEEDED");
    }
}
