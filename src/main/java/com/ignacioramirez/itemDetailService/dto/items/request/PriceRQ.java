package com.ignacioramirez.itemDetailService.dto.items.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record PriceRQ(
        @NotBlank String currency,
        @PositiveOrZero BigDecimal amount
) {}