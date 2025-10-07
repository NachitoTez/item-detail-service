package com.ignacioramirez.itemDetailService.dto.items.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CreateItemRQ(
        @NotBlank String sku,
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String currency,
        @PositiveOrZero BigDecimal amount,
        @PositiveOrZero int stock,
        @NotBlank String sellerId,
        @NotNull String condition,
        boolean freeShipping,
        List<String> categories,
        Map<String,String> attributes
) {}
