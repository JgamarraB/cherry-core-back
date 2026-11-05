package com.cherrycore.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.company.application.CompanyReviewService;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.CustomerFixtures;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.StaffFixtures;
import com.cherrycore.support.StaffFixtures.Owner;
import com.cherrycore.support.TotpCodes;

/**
 * Pasar la propiedad de la empresa y salir de una empresa.
 */
@IntegrationTest
class OwnershipAndLeaveIntegrationTest {

    private static final String INVITATION_SUBJECT_ES = "Te han invitado a unirte a un equipo";
    private static final String NEW_OWNER_SUBJECT_ES = "Ahora eres propietario de la empresa";
    private static final String EVENT_DETAILS_SQL = """
            SELECT details ->> 'companyName' AS company, details ->> 'targetAccountId' AS target
              FROM auth_events WHERE account_id = :accountId AND type = :type
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private CompanyReviewService review;

    @Autowired
    private JdbcClient jdbc;

    private Owner owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = StaffFixtures.activeCompany(mockMvc, mailbox, review, "Motos Levante");
    }

    @Test
    void ownerTransfersOwnershipAndBecomesAdmin() throws Exception {
        Member admin = addMember("ADMIN");

        // Un ADMIN no puede pasar la propiedad.
        String ownerMembership = membershipIdOf(owner.email());
        MvcResult denied = admin.session().call(HttpMethod.POST,
                "/api/company/members/" + ownerMembership + "/transfer-ownership", null);
        assertThat(JsonBodies.<String>read(denied, "$.error.code")).isEqualTo("OWNER_ROLE_RESERVED");

        // Ni a sí mismo.
        MvcResult self = owner.session().call(HttpMethod.POST,
                "/api/company/members/" + ownerMembership + "/transfer-ownership", null);
        assertThat(JsonBodies.<String>read(self, "$.error.code")).isEqualTo("INVALID_OWNERSHIP_TRANSFER");

        MvcResult transferred = owner.session().call(HttpMethod.POST,
                "/api/company/members/" + membershipIdOf(admin.email()) + "/transfer-ownership", null);
        assertThat(transferred.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonBodies.<String>read(transferred, "$.data.roleCode")).isEqualTo("OWNER");

        String body = mailbox.awaitEmail(admin.email(), NEW_OWNER_SUBJECT_ES).textBody();
        assertThat(body).contains("Motos Levante", "Laura");

        MvcResult formerOwner = owner.session().get("/api/company/me");
        assertThat(JsonBodies.<Boolean>read(formerOwner, "$.data.owner")).isFalse();
        assertThat(JsonBodies.<String>read(formerOwner, "$.data.roleCode")).isEqualTo("ADMIN");
        assertThat(JsonBodies.<Boolean>read(admin.session().get("/api/company/me"), "$.data.owner")).isTrue();

        Map<String, Object> event = jdbc.sql(EVENT_DETAILS_SQL)
                .param("accountId", StaffFixtures.accountIdOf(owner.session()))
                .param("type", "OWNERSHIP_TRANSFERRED").query().singleRow();
        assertThat(event.get("company")).isEqualTo("Motos Levante");
        assertThat(event.get("target")).isEqualTo(StaffFixtures.accountIdOf(admin.session()).toString());
    }

    @Test
    void soleOwnerCannotLeave() throws Exception {
        MvcResult attempt = owner.session().call(HttpMethod.POST,
                "/api/account/companies/" + owner.companyId() + "/leave", null);
        assertThat(attempt.getResponse().getStatus()).isEqualTo(422);
        assertThat(JsonBodies.<String>read(attempt, "$.error.code")).isEqualTo("SOLE_OWNER_CANNOT_LEAVE");
    }

    @Test
    void memberLeavesTheActiveCompanyAndTheSessionForgetsIt() throws Exception {
        Member member = addMember("STAFF");
        MvcResult before = member.session().get(AuthPaths.BASE + AuthPaths.SESSION);
        assertThat(JsonBodies.<String>read(before, "$.data.activeCompanyId"))
                .isEqualTo(owner.companyId().toString());

        MvcResult left = member.session().call(HttpMethod.POST,
                "/api/account/companies/" + owner.companyId() + "/leave", null);
        assertThat(left.getResponse().getStatus()).isEqualTo(200);

        MvcResult after = member.session().get(AuthPaths.BASE + AuthPaths.SESSION);
        assertThat(JsonBodies.<Object>read(after, "$.data.activeCompanyId")).isNull();
        assertThat(member.session().get("/api/company").getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonBodies.<List<Object>>read(member.session().get("/api/account/companies"), "$.data"))
                .isEmpty();
        List<String> emails = JsonBodies.read(owner.session().get("/api/company/members"), "$.data[*].email");
        assertThat(emails).doesNotContain(member.email());

        Map<String, Object> event = jdbc.sql(EVENT_DETAILS_SQL)
                .param("accountId", StaffFixtures.accountIdOf(member.session()))
                .param("type", "COMPANY_LEFT").query().singleRow();
        assertThat(event.get("company")).isEqualTo("Motos Levante");

        // Ya no es miembro.
        MvcResult again = member.session().call(HttpMethod.POST,
                "/api/account/companies/" + owner.companyId() + "/leave", null);
        assertThat(JsonBodies.<String>read(again, "$.error.code")).isEqualTo("MEMBER_NOT_FOUND");
    }

    @Test
    void customersCannotLeaveCompanies() throws Exception {
        BrowserSession customer = new BrowserSession(mockMvc);
        customer.login(CustomerFixtures.registerVerified(new BrowserSession(mockMvc), mailbox),
                CustomerFixtures.PASSWORD);
        MvcResult attempt = customer.call(HttpMethod.POST,
                "/api/account/companies/" + owner.companyId() + "/leave", null);
        assertThat(attempt.getResponse().getStatus()).isEqualTo(403);
    }

    private Member addMember(String roleCode) throws Exception {
        String email = CustomerFixtures.uniqueEmail();
        owner.session().call(HttpMethod.POST, "/api/company/invitations",
                "{\"email\": \"" + email + "\", \"roleId\": \"" + roleId(roleCode) + "\"}");
        String token = mailbox.awaitToken(email, INVITATION_SUBJECT_ES);
        new BrowserSession(mockMvc).postJson(AuthPaths.INVITATION_ACCEPT, """
                {"token": "%s", "firstName": "Ana", "lastName": "López", "password": "%s"}
                """.formatted(token, StaffFixtures.PASSWORD));
        BrowserSession member = new BrowserSession(mockMvc);
        member.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        String secret = JsonBodies.read(member.postJson(AuthPaths.MFA_SETUP, "{}"), "$.data.secret");
        member.postJson(AuthPaths.MFA_SETUP_CONFIRM, "{\"code\": \"" + TotpCodes.code(secret, 0) + "\"}");
        return new Member(email, member);
    }

    private String roleId(String code) throws Exception {
        List<String> ids = JsonBodies.read(owner.session().get("/api/company/roles"),
                "$.data[?(@.code == '" + code + "')].id");
        return ids.getFirst();
    }

    private String membershipIdOf(String email) throws Exception {
        List<String> ids = JsonBodies.read(owner.session().get("/api/company/members"),
                "$.data[?(@.email == '" + email + "')].membershipId");
        return ids.getFirst();
    }

    private record Member(String email, BrowserSession session) {
    }
}
