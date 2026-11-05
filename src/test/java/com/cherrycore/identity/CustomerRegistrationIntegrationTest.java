package com.cherrycore.identity;

import static com.cherrycore.support.CustomerFixtures.EXISTING_SUBJECT_ES;
import static com.cherrycore.support.CustomerFixtures.PASSWORD;
import static com.cherrycore.support.CustomerFixtures.VERIFICATION_SUBJECT_ES;
import static com.cherrycore.support.CustomerFixtures.registerBody;
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
class CustomerRegistrationIntegrationTest {

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
    void registerVerifyAndLogin() throws Exception {
        String email = uniqueEmail();

        MvcResult registered = browser.postJson(AuthPaths.CUSTOMER_REGISTER, registerBody(email, PASSWORD));
        assertThat(registered.getResponse().getStatus()).isEqualTo(202);

        // Antes de verificar: contraseña correcta pero email sin verificar.
        MvcResult beforeVerify = browser.login(email, PASSWORD);
        assertThat(beforeVerify.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonBodies.<String>read(beforeVerify, "$.error.code")).isEqualTo("EMAIL_NOT_VERIFIED");

        String token = mailbox.awaitToken(email, VERIFICATION_SUBJECT_ES);
        MvcResult verified = browser.postJson(AuthPaths.CONFIRM_EMAIL, "{\"token\": \"" + token + "\"}");
        assertThat(verified.getResponse().getStatus()).isEqualTo(200);

        MvcResult login = browser.login(email, PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonBodies.<Boolean>read(login, "$.ok")).isTrue();

        MvcResult session = browser.get("http://localhost" + AuthPaths.BASE + AuthPaths.SESSION,
                org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(JsonBodies.<Boolean>read(session, "$.data.authenticated")).isTrue();
        assertThat(JsonBodies.<String>read(session, "$.data.accountType")).isEqualTo("CUSTOMER");
    }

    @Test
    void verificationTokenCanOnlyBeUsedOnce() throws Exception {
        String email = uniqueEmail();
        browser.postJson(AuthPaths.CUSTOMER_REGISTER, registerBody(email, PASSWORD));
        String token = mailbox.awaitToken(email, VERIFICATION_SUBJECT_ES);

        browser.postJson(AuthPaths.CONFIRM_EMAIL, "{\"token\": \"" + token + "\"}");
        MvcResult second = browser.postJson(AuthPaths.CONFIRM_EMAIL, "{\"token\": \"" + token + "\"}");

        assertThat(second.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonBodies.<String>read(second, "$.error.code")).isEqualTo("INVALID_OR_EXPIRED_TOKEN");
    }

    @Test
    void registeringAnExistingEmailLooksIdenticalAndWarnsTheOwner() throws Exception {
        String email = uniqueEmail();
        browser.postJson(AuthPaths.CUSTOMER_REGISTER, registerBody(email, PASSWORD));
        mailbox.awaitEmail(email, VERIFICATION_SUBJECT_ES);

        MvcResult again = browser.postJson(AuthPaths.CUSTOMER_REGISTER,
                registerBody(email.toUpperCase(), "Otra-clave-distinta-1"));

        assertThat(again.getResponse().getStatus()).isEqualTo(202);
        mailbox.awaitEmail(email, EXISTING_SUBJECT_ES);
        long accounts = jdbc.sql("SELECT COUNT(*) FROM accounts WHERE email = ?").param(email)
                .query(Long.class).single();
        assertThat(accounts).isEqualTo(1);
    }

    @Test
    void passwordAndTermsRulesAreEnforced() throws Exception {
        MvcResult shortPassword = browser.postJson(AuthPaths.CUSTOMER_REGISTER, registerBody(uniqueEmail(), "corta"));
        assertThat(JsonBodies.<String>read(shortPassword, "$.error.code")).isEqualTo("PASSWORD_TOO_SHORT");

        String email = uniqueEmail();
        String localPart = email.substring(0, email.indexOf('@'));
        MvcResult containsEmail = browser.postJson(AuthPaths.CUSTOMER_REGISTER,
                registerBody(email, localPart + "-2026"));
        assertThat(JsonBodies.<String>read(containsEmail, "$.error.code")).isEqualTo("PASSWORD_CONTAINS_EMAIL");

        MvcResult noTerms = browser.postJson(AuthPaths.CUSTOMER_REGISTER,
                registerBody(uniqueEmail(), PASSWORD).replace("\"acceptTerms\": true", "\"acceptTerms\": false"));
        assertThat(noTerms.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonBodies.<String>read(noTerms, "$.error.code")).isEqualTo("TERMS_NOT_ACCEPTED");
    }

    @Test
    void passwordIsStoredWithArgon2AndNeverInClear() throws Exception {
        String email = uniqueEmail();
        browser.postJson(AuthPaths.CUSTOMER_REGISTER, registerBody(email, PASSWORD));

        String hash = jdbc.sql("SELECT password_hash FROM accounts WHERE email = ?").param(email)
                .query(String.class).single();
        assertThat(hash).startsWith("{argon2}").doesNotContain(PASSWORD);
    }

    @Test
    void emailsUseTheLanguageOfTheRequest() throws Exception {
        String email = uniqueEmail();
        BrowserSession english = new BrowserSession(mockMvc).withHeader("Accept-Language", "en-GB,en;q=0.9");
        english.postJson(AuthPaths.CUSTOMER_REGISTER, registerBody(email, PASSWORD));

        assertThat(mailbox.awaitEmail(email, "Confirm your email").textBody()).contains("Thanks for signing up");
    }
}
