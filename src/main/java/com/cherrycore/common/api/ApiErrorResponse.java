package com.cherrycore.common.api;

public record ApiErrorResponse(
        boolean success,
        ApiError error
) {

    private static final boolean ERROR_RESPONSE = false;

    public static ApiErrorResponse error(ApiError error) {
        return new ApiErrorResponse(ERROR_RESPONSE, error);
    }
}