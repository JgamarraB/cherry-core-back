package com.cherrycore.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.application.PermissionDefinition;
import com.cherrycore.company.application.CompanyApplicationsService;
import com.cherrycore.company.application.CompanyReviewService;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.OAuthClientSimulator;
import com.cherrycore.support.StaffFixtures;
import com.cherrycore.support.StaffFixtures.Owner;

/**
 * Una app de backoffice (cliente "test-staff-app", app "test-app") recibe en
 * el token la empresa, el rol y los permisos de esa app.
 */
@IntegrationTest
class StaffOAuthIntegrationTest {

    private static final String APP = "test-app";
    private static final String REDIRECT = "http://localhost:8082/login/oauth2/code/cherrycore";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private CompanyReviewService review;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private CompanyApplicationsService companyApplications;

    @Autowired
    private JwtDecoder jwtDecoder;

    private Owner owner;
    private OAuthClientSimulator staffApp;

    @BeforeEach
    void setUp() throws Exception {
        catalog.ensureApplication(APP, "Test App");
        catalog.syncPermissions(APP, List.of(new PermissionDefinition("vehicles.manage", "Manage vehicles"),
                new PermissionDefinition("reservations.view", "View reservations")));
        owner = StaffFixtures.activeCompany(mockMvc, mailbox, review, "Flota Norte");
        staffApp = new OAuthClientSimulator(mockMvc, "test-staff-app", "test-staff-secret", REDIRECT);
    }

    @Test
    void appWithoutActivationForTheCompanyGetsNoToken() throws Exception {
        MvcResult token = authorizeAndExchange();
        assertThat(token.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonBodies.<String>read(token, "$.error")).isEqualTo("access_denied");
    }

    @Test
    void tokenCarriesCompanyRoleAndAppPermissions() throws Exception {
        enableApp();

        MvcResult token = authorizeAndExchange();

        assertThat(token.getResponse().getStatus()).isEqualTo(200);
        Jwt accessToken = jwtDecoder.decode(JsonBodies.read(token, "$.access_token"));
        assertThat(accessToken.getClaimAsString("account_type")).isEqualTo("STAFF");
        assertThat(accessToken.getClaimAsString("tenant_id")).isEqualTo(owner.companyId().toString());
        assertThat(accessToken.getClaimAsString("tenant_role")).isEqualTo("OWNER");
        // Solo permisos de ESTA app (no los de gestión de CherryCore).
        assertThat(accessToken.getClaimAsStringList("permissions"))
                .containsExactly("reservations.view", "vehicles.manage");
    }

    @Test
    void suspendingTheCompanyBlocksTokenRefresh() throws Exception {
        enableApp();
        MvcResult token = authorizeAndExchange();
        String refreshToken = JsonBodies.read(token, "$.refresh_token");

        review.suspend(StaffFixtures.accountIdOf(owner.session()), owner.companyId(), RequestContext.NONE);

        assertThat(staffApp.refresh(refreshToken).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void appCanSyncItsPermissionCatalog() throws Exception {
        List<String> codes = catalog.syncPermissions(APP, List.of(
                new PermissionDefinition("vehicles.manage", "Manage vehicles"),
                new PermissionDefinition("invoices.view", "View invoices"))).stream()
                .map(permission -> permission.code().value()).toList();

        assertThat(codes).containsExactly("invoices.view", "vehicles.manage");
    }

    private MvcResult authorizeAndExchange() throws Exception {
        MvcResult authorized = owner.session().call(HttpMethod.GET, staffApp.authorizationUrl("openid"), null);
        String location = authorized.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(REDIRECT);
        return staffApp.exchangeCode(OAuthClientSimulator.codeFrom(location));
    }

    private void enableApp() throws Exception {
        companyApplications.setEnabled(StaffFixtures.accountIdOf(owner.session()), owner.companyId(),
                catalog.findByCode(APP).orElseThrow().id(), true, RequestContext.NONE);
    }
}
