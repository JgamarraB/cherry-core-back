package com.cherrycore.identity;

import static com.cherrycore.support.CustomerFixtures.PASSWORD;
import static com.cherrycore.support.CustomerFixtures.registerVerified;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.shared.infrastructure.mail.EmailMessage;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;

@IntegrationTest
class NewDeviceAlertIntegrationTest {

    private static final String SUBJECT_ES = "Nuevo inicio de sesión en tu cuenta";
    private static final String DEVICE_COOKIE = "CHERRYCORE_DEVICE";
    private static final String CHROME_ON_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void theFirstLoginOfANewAccountSendsNoAlertButRemembersTheDevice() throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc);
        String email = registerVerified(browser, mailbox);

        MvcResult login = browser.login(email, PASSWORD);

        assertThat(JsonBodies.<String>read(login, "$.data.status")).isEqualTo("AUTHENTICATED");
        String setCookie = login.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(DEVICE_COOKIE + "=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=");
        assertThat(knownDevices(email)).isEqualTo(1);
        assertNoAlert(email);
    }

    @Test
    void aLoginFromANewBrowserSendsAnAlertWithoutTokens() throws Exception {
        BrowserSession first = new BrowserSession(mockMvc);
        String email = registerVerified(first, mailbox);
        first.login(email, PASSWORD);

        BrowserSession other = new BrowserSession(mockMvc).withUserAgent(CHROME_ON_MAC);
        assertThat(JsonBodies.<String>read(other.login(email, PASSWORD), "$.data.status")).isEqualTo("AUTHENTICATED");

        EmailMessage alert = mailbox.awaitEmail(email, SUBJECT_ES);
        assertThat(alert.textBody()).contains("Chrome en macOS").contains("127.0.0.1")
                .contains("http://localhost:4200/account/activity");
        assertThat(alert.textBody() + alert.htmlBody()).doesNotContain("token=");
        assertThat(knownDevices(email)).isEqualTo(2);
        assertThat(events(email, "NEW_DEVICE_LOGIN")).isEqualTo(1);
    }

    @Test
    void aKnownBrowserSendsNothingAgain() throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc);
        String email = registerVerified(browser, mailbox);
        browser.login(email, PASSWORD);
        browser.postJson("/logout", "{}");
        browser.login(email, PASSWORD);

        BrowserSession other = new BrowserSession(mockMvc);
        other.login(email, PASSWORD);
        mailbox.awaitEmail(email, SUBJECT_ES);
        other.postJson("/logout", "{}");
        other.login(email, PASSWORD);

        assertThat(knownDevices(email)).isEqualTo(2);
        Awaitility.await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .until(() -> alerts(email) == 1);
    }

    @Test
    void aForgedDeviceCookieCountsAsANewDevice() throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc);
        String email = registerVerified(browser, mailbox);
        browser.login(email, PASSWORD);
        browser.postJson("/logout", "{}");

        BrowserSession forged = new BrowserSession(mockMvc);
        forged.withHeader(HttpHeaders.COOKIE, DEVICE_COOKIE + "=not-a-valid-device-id");
        forged.login(email, PASSWORD);

        mailbox.awaitEmail(email, SUBJECT_ES);
    }

    private void assertNoAlert(String email) {
        Awaitility.await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .until(() -> alerts(email) == 0);
    }

    private long alerts(String email) {
        return mailbox.sentTo(email).stream().filter(message -> message.subject().equals(SUBJECT_ES)).count();
    }

    private long knownDevices(String email) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM known_devices d JOIN accounts a ON a.id = d.account_id
                 WHERE a.email = ? AND d.deleted_at IS NULL
                """).param(email).query(Long.class).single();
    }

    private long events(String email, String type) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM auth_events e JOIN accounts a ON a.id = e.account_id
                 WHERE a.email = ? AND e.type = ?
                """).param(email).param(type).query(Long.class).single();
    }
}
