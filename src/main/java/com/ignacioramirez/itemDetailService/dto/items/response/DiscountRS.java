package com.ignacioramirez.itemDetailService.dto.items.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record DiscountRS(
        String type,
        long value,
        String label,
        Instant startsAt,
        Instant endsAt,
        boolean active
) {}
