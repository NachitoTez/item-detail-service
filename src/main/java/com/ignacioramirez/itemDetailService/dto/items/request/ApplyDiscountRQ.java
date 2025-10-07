package com.ignacioramirez.itemDetailService.dto.items.request;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record ApplyDiscountRQ(
        @NotBlank String type,
        @PositiveOrZero long value,
        String label,
        Instant startsAt,
        Instant endsAt
) {}
