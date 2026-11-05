package com.cherrycore.shared.infrastructure.web;

import static com.cherrycore.support.ErrorScenariosTestController.ADMIN_ONLY;
import static com.cherrycore.support.ErrorScenariosTestController.BASE_PATH;
import static com.cherrycore.support.ErrorScenariosTestController.DOMAIN;
import static com.cherrycore.support.ErrorScenariosTestController.OK;
import static com.cherrycore.support.ErrorScenariosTestController.REQUIRED_PARAM;
import static com.cherrycore.support.ErrorScenariosTestController.SECRET_INTERNAL_MESSAGE;
import static com.cherrycore.support.ErrorScenariosTestController.UNEXPECTED;
import static com.cherrycore.support.ErrorScenariosTestController.VALIDATION;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cherrycore.support.IntegrationTest;

@IntegrationTest
@WithMockUser
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successUsesOkEnvelope() throws Exception {
        mockMvc.perform(get(BASE_PATH + OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.value").value("pong"));
    }

    @Test
    void domainErrorUsesCategoryStatusAndCode() throws Exception {
        expectError(mockMvc.perform(get(BASE_PATH + DOMAIN)), 409, "TEST_CONFLICT");
    }

    @Test
    void validationErrorListsFieldsWithoutEchoingValues() throws Exception {
        String secretPassword = "  ";
        String body = """
                {"email": "not-an-email", "password": "%s"}
                """.formatted(secretPassword);

        expectError(postJson(VALIDATION, body), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.error.fields[*].field").value(containsInAnyOrder("email", "password")))
                .andExpect(jsonPath("$.error.fields[*].code").value(containsInAnyOrder("EMAIL", "NOT_BLANK")))
                .andExpect(content().string(not(containsString("not-an-email"))));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        expectError(postJson(VALIDATION, "{ broken"), 400, "MALFORMED_REQUEST");
    }

    @Test
    void unknownJsonFieldsAreRejected() throws Exception {
        String body = """
                {"email": "a@b.com", "password": "x", "role": "ADMIN"}
                """;
        expectError(postJson(VALIDATION, body), 400, "MALFORMED_REQUEST");
    }

    @Test
    void wrongParameterTypeIsBadRequest() throws Exception {
        expectError(mockMvc.perform(get(BASE_PATH + "/typed/abc")), 400, "INVALID_PARAMETER");
    }

    @Test
    void missingParameterIsBadRequest() throws Exception {
        expectError(mockMvc.perform(get(BASE_PATH + REQUIRED_PARAM)), 400, "MISSING_PARAMETER");
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        expectError(mockMvc.perform(get("/api/does-not-exist")), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        expectError(mockMvc.perform(post(BASE_PATH + OK)), 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        expectError(mockMvc.perform(post(BASE_PATH + VALIDATION)
                .contentType(MediaType.TEXT_PLAIN).content("x")), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unexpectedErrorNeverLeaksInternals() throws Exception {
        expectError(mockMvc.perform(get(BASE_PATH + UNEXPECTED)), 500, "INTERNAL_ERROR")
                .andExpect(content().string(not(containsString(SECRET_INTERNAL_MESSAGE))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void missingRoleIsForbidden() throws Exception {
        expectError(mockMvc.perform(get(BASE_PATH + ADMIN_ONLY)), 403, "ACCESS_DENIED");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void requiredRoleIsAllowed() throws Exception {
        mockMvc.perform(get(BASE_PATH + ADMIN_ONLY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(BASE_PATH + path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static ResultActions expectError(ResultActions actions, int httpStatus, String code) throws Exception {
        return actions
                .andExpect(status().is(httpStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.description").isNotEmpty());
    }
}
