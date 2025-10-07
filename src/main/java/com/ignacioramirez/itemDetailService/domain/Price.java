package com.ignacioramirez.itemDetailService.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Price(String currency, BigDecimal amount) {

    public Price {
        Objects.requireNonNull(currency, "currency required");
        Objects.requireNonNull(amount, "amount required");

        if (!currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currency must be ISO 4217 (e.g., ARS, USD)");
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }

        // Normalizamos el scale a 2 decimales
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public Price withAmount(BigDecimal newAmount) {
        Objects.requireNonNull(newAmount, "newAmount required");
        return new Price(currency, newAmount);
    }
}
