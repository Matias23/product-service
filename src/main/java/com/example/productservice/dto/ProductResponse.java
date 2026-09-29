package com.example.productservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String sku,
        Instant createdAt,
        Instant updatedAt) {
}
