package com.ignacioramirez.itemDetailService.dto.items.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateItemRQ(
        @Size(min = 1, message = "title must not be blank if provided")
        String title,

        @Size(min = 1, message = "description must not be blank if provided")
        String description,

        @Valid
        PriceRQ price,

        @PositiveOrZero(message = "stock must be >= 0 if provided")
        Integer stock
) {}