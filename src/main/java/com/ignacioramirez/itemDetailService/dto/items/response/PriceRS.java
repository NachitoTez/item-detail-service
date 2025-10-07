package com.ignacioramirez.itemDetailService.dto.items.response;

import com.fasterxml.jackson.annotation.JsonInclude;


import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record PriceRS(
        BigDecimal amount,
        String currency
) { }
