package com.ignacioramirez.itemDetailService.dto.items.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public record CreateItemRQ(
        @NotBlank String title,
        @NotBlank String description,
        @Valid @NotNull PriceRQ price,
        @PositiveOrZero int stock,
        @NotBlank String sellerId,
        @NotNull String condition,
        boolean freeShipping,
        List<String> categories,
        Map<String, String> attributes
) {}