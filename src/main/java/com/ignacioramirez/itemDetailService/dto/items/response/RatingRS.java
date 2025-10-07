package com.ignacioramirez.itemDetailService.dto.items.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ignacioramirez.itemDetailService.domain.Rating;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record RatingRS(
        double average,
        int count
) {
    public static RatingRS from(Rating r) {
        if (r == null) return null;
        return new RatingRS(r.average(), r.count());
    }
}
