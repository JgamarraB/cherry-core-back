package com.cherrycore.common.api;

import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, String> details
) {
}