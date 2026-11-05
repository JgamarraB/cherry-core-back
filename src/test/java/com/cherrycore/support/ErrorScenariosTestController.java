package com.cherrycore.support;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.error.ErrorCategory;
import com.cherrycore.shared.domain.error.ErrorCode;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Controller solo para tests: provoca cada tipo de error para comprobar
 * que el formato KO y los estados HTTP son correctos.
 */
@RestController
@RequestMapping(ErrorScenariosTestController.BASE_PATH)
public class ErrorScenariosTestController {

    public static final String BASE_PATH = "/api/test/errors";
    public static final String OK = "/ok";
    public static final String DOMAIN = "/domain";
    public static final String VALIDATION = "/validation";
    public static final String TYPED_PARAM = "/typed/{id}";
    public static final String REQUIRED_PARAM = "/required-param";
    public static final String UNEXPECTED = "/unexpected";
    public static final String ADMIN_ONLY = "/admin-only";

    public static final String SECRET_INTERNAL_MESSAGE = "db password is hunter2";

    public enum TestErrorCode implements ErrorCode {
        TEST_CONFLICT(ErrorCategory.CONFLICT, "Test conflict");

        private final ErrorCategory category;
        private final String description;

        TestErrorCode(ErrorCategory category, String description) {
            this.category = category;
            this.description = description;
        }

        @Override
        public String code() {
            return name();
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public ErrorCategory category() {
            return category;
        }
    }

    public record TestRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record TestData(String value) {
    }

    @GetMapping(OK)
    public ApiSuccessResponse<TestData> ok() {
        return ApiSuccessResponse.of(new TestData("pong"));
    }

    @GetMapping(DOMAIN)
    public void domain() {
        throw new DomainException(TestErrorCode.TEST_CONFLICT);
    }

    @PostMapping(path = VALIDATION, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiSuccessResponse<TestData> validation(@Valid @RequestBody TestRequest request) {
        return ApiSuccessResponse.of(new TestData(request.email()));
    }

    @GetMapping(TYPED_PARAM)
    public ApiSuccessResponse<Long> typed(@PathVariable Long id) {
        return ApiSuccessResponse.of(id);
    }

    @GetMapping(REQUIRED_PARAM)
    public ApiSuccessResponse<String> requiredParam(@RequestParam String name) {
        return ApiSuccessResponse.of(name);
    }

    @GetMapping(UNEXPECTED)
    public void unexpected() {
        throw new IllegalStateException(SECRET_INTERNAL_MESSAGE);
    }

    @GetMapping(ADMIN_ONLY)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiSuccessResponse<TestData> adminOnly() {
        return ApiSuccessResponse.of(new TestData("admin"));
    }
}
