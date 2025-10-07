package com.ignacioramirez.itemDetailService.dto.items.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateItemRQ(
        @NotBlank String title,
        @NotBlank String description,
        @PositiveOrZero BigDecimal amount,
        @PositiveOrZero int stock
) {}
