package com.cherrycore.identity;

import static com.cherrycore.support.CustomerFixtures.PASSWORD;
import static com.cherrycore.support.CustomerFixtures.registerVerified;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.StaffFixtures;

@IntegrationTest
class SecurityActivityIntegrationTest {

    private static final String ACTIVITY = "/api/account/activity";
    private static final String FIREFOX_ON_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private StaffUserRepository staffUsers;

    @Test
    void showsMyRecentEventsWithTheDeviceAndMarksTheCurrentOne() throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc).withUserAgent(FIREFOX_ON_WINDOWS);
        String email = registerVerified(browser, mailbox);
        browser.login(email, PASSWORD);

        MvcResult result = browser.get(ACTIVITY + "?page=0&size=5");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> items = JsonBodies.read(result, "$.data.items");
        assertThat(items).extracting(item -> item.get("type")).contains("LOGIN_COMPLETED", "EMAIL_VERIFIED")
                .doesNotContain("LOGIN_SUCCEEDED");
        Map<String, Object> login = items.getFirst();
        assertThat(login).containsEntry("type", "LOGIN_COMPLETED")
                .containsEntry("ip", "127.0.0.1")
                .containsEntry("browser", "Firefox")
                .containsEntry("os", "Windows")
                .containsEntry("byAdministrator", false)
                .containsEntry("currentDevice", true);
        assertThat(JsonBodies.<Integer>read(result, "$.data.size")).isEqualTo(5);
    }

    @Test
    void actionsByAnAdministratorHideTheirIpAndBrowser() throws Exception {
        BrowserSession customer = new BrowserSession(mockMvc);
        String email = registerVerified(customer, mailbox);
        customer.login(email, PASSWORD);
        String customerId = JsonBodies.read(customer.get("/api/account"), "$.data.id");

        String adminEmail = StaffFixtures.createSuperAdmin(accounts, hasher, staffUsers);
        BrowserSession admin = new BrowserSession(mockMvc).withUserAgent(FIREFOX_ON_WINDOWS);
        StaffFixtures.firstLoginWithMfaSetup(admin, adminEmail);
        assertThat(admin.call(HttpMethod.POST, "/api/admin/accounts/" + customerId + "/disable", "{}")
                .getResponse().getStatus()).isEqualTo(200);
        admin.call(HttpMethod.POST, "/api/admin/accounts/" + customerId + "/enable", "{}");

        BrowserSession again = new BrowserSession(mockMvc);
        again.login(email, PASSWORD);
        List<Map<String, Object>> items = JsonBodies.read(again.get(ACTIVITY), "$.data.items");

        Map<String, Object> disabled = items.stream()
                .filter(item -> "ACCOUNT_DISABLED_BY_ADMIN".equals(item.get("type"))).findFirst().orElseThrow();
        assertThat(disabled).containsEntry("byAdministrator", true)
                .containsEntry("ip", null)
                .containsEntry("browser", null)
                .containsEntry("os", null)
                .containsEntry("currentDevice", false);
    }

    @Test
    void requiresASession() throws Exception {
        assertThat(new BrowserSession(mockMvc).get(ACTIVITY).getResponse().getStatus()).isEqualTo(401);
    }
}
