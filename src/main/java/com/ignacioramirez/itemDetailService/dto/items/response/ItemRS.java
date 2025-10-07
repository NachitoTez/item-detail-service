package com.ignacioramirez.itemDetailService.dto.items.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ignacioramirez.itemDetailService.domain.*;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;


@JsonInclude(NON_NULL)
public record ItemRS(
        String id,
        String sku,
        String title,
        String description,
        PriceRS basePrice,
        PriceRS currentPrice,
        Boolean hasActiveDiscount,
        DiscountRS discount,
        Integer stock,
        String sellerId,
        List<PictureRS> pictures,
        RatingRS rating,
        Condition condition,
        Boolean freeShipping,
        List<String> categories,
        Map<String, String> attributes
) { }
