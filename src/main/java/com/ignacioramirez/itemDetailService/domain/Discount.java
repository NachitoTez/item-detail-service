package com.ignacioramirez.itemDetailService.domain;

import java.time.Instant;

public record Discount(DiscountType type, long value, String label, Instant startsAt, Instant endsAt) {
    public Discount {
        if (type == null) throw new IllegalArgumentException("type required");
        if (type == DiscountType.PERCENT && (value < 0 || value > 100))
            throw new IllegalArgumentException("percent 0..100");
        if (type == DiscountType.AMOUNT && value < 0)
            throw new IllegalArgumentException("amount >= 0");
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt))
            throw new IllegalArgumentException("endsAt before startsAt");
    }
    public boolean isActive(Instant now) {
        now = (now == null) ? Instant.now() : now;
        return (startsAt == null || !now.isBefore(startsAt)) &&
                (endsAt == null   || !now.isAfter(endsAt));
    }
}
