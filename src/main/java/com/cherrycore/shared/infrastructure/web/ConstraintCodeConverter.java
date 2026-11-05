package com.cherrycore.shared.infrastructure.web;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Convierte el nombre de una validación de Bean Validation al formato de
 * código de la API: {@code NotBlank} → {@code NOT_BLANK}.
 */
public final class ConstraintCodeConverter {

    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final String SNAKE_CASE_REPLACEMENT = "$1_$2";
    private static final String UNKNOWN_CONSTRAINT = "INVALID";

    private ConstraintCodeConverter() {
    }

    public static String toCode(String constraintName) {
        if (constraintName == null || constraintName.isBlank()) {
            return UNKNOWN_CONSTRAINT;
        }
        return CAMEL_CASE_BOUNDARY.matcher(constraintName)
                .replaceAll(SNAKE_CASE_REPLACEMENT)
                .toUpperCase(Locale.ROOT);
    }
}
