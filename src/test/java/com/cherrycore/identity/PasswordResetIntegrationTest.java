package com.cherrycore.identity;

import static com.cherrycore.support.CustomerFixtures.PASSWORD;
import static com.cherrycore.support.CustomerFixtures.PASSWORD_CHANGED_SUBJECT_ES;
import static com.cherrycore.support.CustomerFixtures.RESET_SUBJECT_ES;
import static com.cherrycore.support.CustomerFixtures.registerVerified;
import static com.cherrycore.support.CustomerFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;

@IntegrationTest
class PasswordResetIntegrationTest {

    private static final String NEW_PASSWORD = "Nueva-clave-segura-2027";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    private BrowserSession browser;

    @BeforeEach
    void setUp() {
        browser = new BrowserSession(mockMvc);
    }

    @Test
    void forgotPasswordAnswersTheSameWhetherTheEmailExistsOrNot() throws Exception {
        String existing = registerVerified(browser, mailbox);
        String unknown = uniqueEmail();

        MvcResult forExisting = forgot(existing);
        MvcResult forUnknown = forgot(unknown);

        assertThat(forExisting.getResponse().getStatus()).isEqualTo(202);
        assertThat(forUnknown.getResponse().getStatus()).isEqualTo(202);
        assertThat(forUnknown.getResponse().getContentAsString())
                .isEqualTo(forExisting.getResponse().getContentAsString());
        mailbox.awaitEmail(existing, RESET_SUBJECT_ES);
        Awaitility.await().during(Duration.ofMillis(500)).until(() -> mailbox.sentTo(unknown).isEmpty());
    }

    @Test
    void resetChangesThePasswordClosesSessionsAndTheLinkWorksOnce() throws Exception {
        String email = registerVerified(browser, mailbox);
        browser.login(email, PASSWORD);

        forgot(email);
        String token = mailbox.awaitToken(email, RESET_SUBJECT_ES);
        MvcResult reset = reset(token, NEW_PASSWORD);
        assertThat(reset.getResponse().getStatus()).isEqualTo(200);
        mailbox.awaitEmail(email, PASSWORD_CHANGED_SUBJECT_ES);

        // La sesión que estaba abierta se ha cerrado.
        MvcResult session = browser.get("http://localhost" + AuthPaths.BASE + AuthPaths.SESSION,
                MediaType.APPLICATION_JSON);
        assertThat(JsonBodies.<Boolean>read(session, "$.data.authenticated")).isFalse();

        BrowserSession fresh = new BrowserSession(mockMvc);
        assertThat(fresh.login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(fresh.login(email, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);

        MvcResult reused = reset(token, "Otra-clave-mas-2028");
        assertThat(JsonBodies.<String>read(reused, "$.error.code")).isEqualTo("INVALID_OR_EXPIRED_TOKEN");
    }

    @Test
    void requestingANewLinkInvalidatesThePreviousOne() throws Exception {
        String email = registerVerified(browser, mailbox);
        forgot(email);
        String firstToken = mailbox.awaitToken(email, RESET_SUBJECT_ES);
        mailbox.clear();
        forgot(email);
        mailbox.awaitToken(email, RESET_SUBJECT_ES);

        MvcResult withOldLink = reset(firstToken, NEW_PASSWORD);
        assertThat(JsonBodies.<String>read(withOldLink, "$.error.code")).isEqualTo("INVALID_OR_EXPIRED_TOKEN");
    }

    @Test
    void newPasswordMustFollowThePolicy() throws Exception {
        String email = registerVerified(browser, mailbox);
        forgot(email);
        String token = mailbox.awaitToken(email, RESET_SUBJECT_ES);

        MvcResult weak = reset(token, "corta");
        assertThat(JsonBodies.<String>read(weak, "$.error.code")).isEqualTo("PASSWORD_TOO_SHORT");
    }

    private MvcResult forgot(String email) throws Exception {
        return browser.postJson(AuthPaths.CUSTOMER_FORGOT_PASSWORD, "{\"email\": \"" + email + "\"}");
    }

    private MvcResult reset(String token, String newPassword) throws Exception {
        return browser.postJson(AuthPaths.RESET_PASSWORD,
                "{\"token\": \"" + token + "\", \"newPassword\": \"" + newPassword + "\"}");
    }
}
