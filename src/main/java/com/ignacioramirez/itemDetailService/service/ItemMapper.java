package com.ignacioramirez.itemDetailService.service;

import com.ignacioramirez.itemDetailService.domain.*;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ItemMapper {

    // ---------- Commands -> Domain ----------
    public static Item toNewDomain(CreateItemRQ rq) {
        String condition = Objects.requireNonNull(rq.condition(), "condition").toUpperCase(Locale.ROOT);

        return new ItemBuilder()
                .title(rq.title())
                .description(rq.description())
                .price(new Price(rq.price().currency().toUpperCase(), rq.price().amount()))
                .stock(rq.stock())
                .sellerId(rq.sellerId())
                .condition(Condition.valueOf(condition))
                .freeShipping(rq.freeShipping())
                .categories(Objects.requireNonNullElse(rq.categories(), List.of()))
                .attributes(Objects.requireNonNullElse(rq.attributes(), Map.of()))
                .build();
    }

    public static void applyUpdate(Item item, UpdateItemRQ rq) {
        if (rq.title() != null) {
            item.changeTitle(rq.title());
        }

        if (rq.description() != null) {
            item.changeDescription(rq.description());
        }

        if (rq.price() != null) {
            Price newPrice = new Price(
                    rq.price().currency().toUpperCase(),
                    rq.price().amount()
            );
            item.changeBasePrice(newPrice);
        }

        if (rq.stock() != null) {
            item.setStock(rq.stock());
        }
    }

    // ---------- Domain -> Responses ----------
    public static ItemRS toRS(Item item, Instant now) {
        var basePrice    = mapPrice(item.getBasePrice());
        var currentPrice = mapPrice(item.getCurrentPrice(now));
        var activeDisc   = item.getActiveDiscount(now);
        var discountRS   = activeDisc.map(d -> new DiscountRS(
                d.type().name(),
                d.value(),
                d.label(),
                d.startsAt(),
                d.endsAt(),
                d.isActive(now)
        )).orElse(null);

        var pictures = item.getPictures().stream().map(ItemMapper::mapPicture).toList();
        var ratingRS = mapRating(item.getRating());

        return new ItemRS(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                basePrice,
                currentPrice,
                activeDisc.isPresent(),
                discountRS,
                item.getStock(),
                item.getSellerId(),
                pictures,
                ratingRS,
                item.getCondition(),
                item.isFreeShipping(),
                item.getCategories(),
                item.getAttributes()
        );
    }


    // ---------- Helpers ----------
    private static PriceRS mapPrice(Price price) {
        return new PriceRS(price.amount(), price.currency());
    }

    private static DiscountRS mapDiscount(Discount d, Instant now) {
        return new DiscountRS(
                d.type().name(),
                d.value(),
                d.label(),
                d.startsAt(),
                d.endsAt(),
                d.isActive(now)
        );
    }


    private static PictureRS mapPicture(Picture p) {
        return new PictureRS(p.url(), p.main(), p.alt());
    }

    private static RatingRS mapRating(Rating rating) {
        return RatingRS.from(rating);
    }
}
