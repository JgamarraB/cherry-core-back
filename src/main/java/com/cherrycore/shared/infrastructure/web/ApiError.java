package com.cherrycore.shared.infrastructure.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Objeto error de la respuesta KO.
 */
public record ApiError(
        String code,
        String description,
        @JsonInclude(Include.NON_EMPTY) List<ApiFieldError> fields
) {

    public ApiError {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
