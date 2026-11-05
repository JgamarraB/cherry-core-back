package com.cherrycore.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ConstraintCodeConverterTest {

    @ParameterizedTest
    @CsvSource({
            "NotBlank, NOT_BLANK",
            "Email, EMAIL",
            "Size, SIZE",
            "DecimalMin, DECIMAL_MIN",
            "PastOrPresent, PAST_OR_PRESENT"
    })
    void convertsConstraintNameToApiCode(String constraint, String expected) {
        assertThat(ConstraintCodeConverter.toCode(constraint)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void fallsBackToInvalidWhenNameIsMissing(String constraint) {
        assertThat(ConstraintCodeConverter.toCode(constraint)).isEqualTo("INVALID");
    }
}
