package com.cherrycore.shared.infrastructure.web;

/**
 * Respuesta OK: {@code { "ok": true, "data": ... }}.
 */
public record ApiSuccessResponse<T>(boolean ok, T data) {

    public static <T> ApiSuccessResponse<T> of(T data) {
        return new ApiSuccessResponse<>(true, data);
    }
}
