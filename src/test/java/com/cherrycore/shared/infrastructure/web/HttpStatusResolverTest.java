package com.cherrycore.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import com.cherrycore.shared.domain.error.ErrorCategory;

class HttpStatusResolverTest {

    @ParameterizedTest
    @CsvSource({
            "INVALID_INPUT, BAD_REQUEST",
            "UNAUTHENTICATED, UNAUTHORIZED",
            "FORBIDDEN, FORBIDDEN",
            "NOT_FOUND, NOT_FOUND",
            "CONFLICT, CONFLICT",
            "BUSINESS_RULE, UNPROCESSABLE_CONTENT",
            "TOO_MANY_REQUESTS, TOO_MANY_REQUESTS",
            "INTERNAL, INTERNAL_SERVER_ERROR"
    })
    void mapsEveryCategoryToItsHttpStatus(ErrorCategory category, HttpStatus expected) {
        assertThat(HttpStatusResolver.resolve(category)).isEqualTo(expected);
    }
}
