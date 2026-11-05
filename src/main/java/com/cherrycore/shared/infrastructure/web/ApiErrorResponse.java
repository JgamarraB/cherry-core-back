package com.cherrycore.shared.infrastructure.web;

import java.util.List;

import com.cherrycore.shared.domain.error.ErrorCode;

/**
 * Respuesta KO: {@code { "ok": false, "error": { "code", "description", "fields" } }}.
 */
public record ApiErrorResponse(boolean ok, ApiError error) {

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return of(errorCode.code(), errorCode.description(), List.of());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, List<ApiFieldError> fields) {
        return of(errorCode.code(), errorCode.description(), fields);
    }

    public static ApiErrorResponse of(WebErrorCode errorCode) {
        return of(errorCode.code(), errorCode.description(), List.of());
    }

    private static ApiErrorResponse of(String code, String description, List<ApiFieldError> fields) {
        return new ApiErrorResponse(false, new ApiError(code, description, fields));
    }
}
