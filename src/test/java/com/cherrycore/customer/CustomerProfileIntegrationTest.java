package com.cherrycore.customer;

import static com.cherrycore.support.CustomerFixtures.registerVerified;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;

@IntegrationTest
class CustomerProfileIntegrationTest {

    private static final String ME = "/api/customers/me";
    private static final String ADDRESSES = ME + "/addresses";
    private static final String ADDRESS = """
            {"line1": "Calle Mayor 1", "city": "Madrid", "postalCode": "28013", "countryCode": "es", "isDefault": %s}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private JdbcClient jdbc;

    private RequestPostProcessor customer;

    @BeforeEach
    void setUp() throws Exception {
        customer = tokenFor(registerVerified(new BrowserSession(mockMvc), mailbox));
    }

    @Test
    void updatesProfileAndLanguage() throws Exception {
        MvcResult updated = mockMvc.perform(put(ME).with(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \" Lucía \", \"lastName\": \"Pérez\", \"phone\": null, \"locale\": \"en\"}"))
                .andReturn();

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonBodies.<String>read(updated, "$.data.firstName")).isEqualTo("Lucía");
        assertThat(JsonBodies.<String>read(updated, "$.data.locale")).isEqualTo("en");
        assertThat(JsonBodies.<Object>read(updated, "$.data.phone")).isNull();
    }

    @Test
    void rejectsInvalidPhoneAndUnsupportedLanguage() throws Exception {
        MvcResult badPhone = mockMvc.perform(put(ME).with(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \"A\", \"lastName\": \"B\", \"phone\": \"600111222\"}")).andReturn();
        assertThat(JsonBodies.<String>read(badPhone, "$.error.code")).isEqualTo("INVALID_PHONE");

        MvcResult badLocale = mockMvc.perform(put(ME).with(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \"A\", \"lastName\": \"B\", \"locale\": \"ja\"}")).andReturn();
        assertThat(JsonBodies.<String>read(badLocale, "$.error.code")).isEqualTo("UNSUPPORTED_LOCALE");
    }

    @Test
    void manageAddressesWithSingleDefault() throws Exception {
        String first = addAddress(customer, false);
        String second = addAddress(customer, true);

        MvcResult list = mockMvc.perform(get(ADDRESSES).with(customer)).andReturn();
        List<String> defaults = JsonBodies.read(list, "$.data[?(@.isDefault == true)].id");
        assertThat(defaults).containsExactly(second);
        assertThat(JsonBodies.<String>read(list, "$.data[0].countryCode")).isEqualTo("ES");

        mockMvc.perform(delete(ADDRESSES + "/" + first).with(customer));
        MvcResult afterDelete = mockMvc.perform(get(ADDRESSES).with(customer)).andReturn();
        assertThat(JsonBodies.<List<String>>read(afterDelete, "$.data[*].id")).containsExactly(second);

        // Borrado lógico: sigue en la base de datos con fecha y autor.
        var deletedBy = jdbc.sql("SELECT deleted_by FROM customer_addresses WHERE id = ?")
                .param(UUID.fromString(first)).query(UUID.class).single();
        assertThat(deletedBy).isNotNull();
    }

    @Test
    void cannotTouchAnotherCustomersAddress() throws Exception {
        String victimsAddress = addAddress(customer, false);
        RequestPostProcessor attacker = tokenFor(registerVerified(new BrowserSession(mockMvc), mailbox));

        MvcResult update = mockMvc.perform(put(ADDRESSES + "/" + victimsAddress).with(attacker)
                .contentType(MediaType.APPLICATION_JSON).content(ADDRESS.formatted(false))).andReturn();
        MvcResult remove = mockMvc.perform(delete(ADDRESSES + "/" + victimsAddress).with(attacker)).andReturn();

        assertThat(update.getResponse().getStatus()).isEqualTo(404);
        assertThat(remove.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void staffTokensCannotUseCustomerEndpoints() throws Exception {
        RequestPostProcessor staff = jwt().jwt(token -> token.subject(UUID.randomUUID().toString())
                .claim("account_type", "STAFF")).authorities(new SimpleGrantedAuthority("ROLE_STAFF"));

        MvcResult result = mockMvc.perform(get(ME).with(staff)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    private String addAddress(RequestPostProcessor who, boolean isDefault) throws Exception {
        MvcResult created = mockMvc.perform(post(ADDRESSES).with(who).contentType(MediaType.APPLICATION_JSON)
                .content(ADDRESS.formatted(isDefault))).andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        return JsonBodies.read(created, "$.data.id");
    }

    private RequestPostProcessor tokenFor(String email) {
        UUID accountId = jdbc.sql("SELECT id FROM accounts WHERE email = ?").param(email).query(UUID.class).single();
        return jwt().jwt(token -> token.subject(accountId.toString()).claim("account_type", "CUSTOMER"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
