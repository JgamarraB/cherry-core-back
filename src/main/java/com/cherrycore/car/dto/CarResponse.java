package com.cherrycore.car.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CarResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}