package com.cherrycore.car.dto;

import static com.cherrycore.car.validation.CarValidationMessages.PRICE_MIN_VALUE;
import static com.cherrycore.car.validation.CarValidationMessages.PRICE_REQUIRED;
import static com.cherrycore.car.validation.CarValidationMessages.TITLE_MAX_LENGTH;
import static com.cherrycore.car.validation.CarValidationMessages.TITLE_REQUIRED;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCarRequest(

		@NotBlank(message = TITLE_REQUIRED)
        @Size(
                max = 150,
                message = TITLE_MAX_LENGTH
        )
        String title,

        String description,

        @NotNull(message = PRICE_REQUIRED)
        @DecimalMin(
                value = "0.00",
                inclusive = true,
                message = PRICE_MIN_VALUE
        )
        BigDecimal price


) {
}