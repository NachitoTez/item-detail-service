package com.ignacioramirez.itemDetailService.service;

import com.ignacioramirez.itemDetailService.domain.*;
import com.ignacioramirez.itemDetailService.dto.items.request.ApplyDiscountRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.exceptions.ApiException;
import com.ignacioramirez.itemDetailService.exceptions.ConflictException;
import com.ignacioramirez.itemDetailService.exceptions.NotFoundException;
import com.ignacioramirez.itemDetailService.repository.ItemRepository;
import com.ignacioramirez.itemDetailService.service.utils.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class ItemServiceImpl implements ItemService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemServiceImpl.class);

    private final ItemRepository repo;
    private final Clock clock;

    public ItemServiceImpl(ItemRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    // ----------------------- CREATE -----------------------
    @Override
    public ItemRS create(CreateItemRQ rq) {
        LOGGER.info("Creating item with title='{}' from {}", rq.title(), rq.sellerId());

        String titleNorm = Texts.normalizeTitle(rq.title());

        repo.findBySellerAndTitleNormalized(rq.sellerId(), titleNorm).ifPresent(existing -> {
            throw new ConflictException("Duplicate listing: same title already published by this seller");
        });

        Item item = ItemMapper.toNewDomain(rq);
        item.validate();

        try {
            Item saved = repo.save(item);
            LOGGER.info("Item created successfully with id='{}'", saved.getId());
            return ItemMapper.toRS(saved, clock.instant());
        } catch (IllegalStateException e) {
            throw new ConflictException("Duplicate listing: same title already published by this seller");
        }
    }

    // ----------------------- READ -----------------------
    @Override
    public ItemRS getById(String id) {
        LOGGER.info("Retrieving item with id='{}'", id);

        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        LOGGER.info("Item found with id='{}'", id);
        return ItemMapper.toRS(item, clock.instant());
    }

    @Override
    public List<ItemRS> list(int page, int size) {
        LOGGER.info("Listing items with page={}, size={}", page, size);

        List<Item> items = repo.findAll(page, size);
        Instant now = clock.instant();

        LOGGER.info("Returning {} items", items.size());
        return items.stream()
                .map(item -> ItemMapper.toRS(item, now))
                .toList();
    }

    // ----------------------- UPDATE -----------------------
    @Override
    public ItemRS update(String id, UpdateItemRQ rq) {
        LOGGER.info("Updating item with id='{}'", id);

        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        String oldNorm = item.getTitleNormalized();

        ItemMapper.applyUpdate(item, rq);

        if (rq.title() != null) {
            String newNorm = Texts.normalizeTitle(item.getTitle());

            if (!Objects.equals(oldNorm, newNorm)) {
                repo.findBySellerAndTitleNormalized(item.getSellerId(), newNorm).ifPresent(existing -> {
                    if (!existing.getId().equals(item.getId())) {
                        throw new ConflictException("Duplicate listing: same title already published by this seller");
                    }
                });
            }
        }

        item.validate();

        try {
            Item saved = repo.save(item);
            LOGGER.info("Item updated successfully with id='{}'", id);
            return ItemMapper.toRS(saved, clock.instant());
        } catch (IllegalStateException e) {
            throw new ConflictException("Duplicate listing: same title already published by this seller");
        }
    }

    // ----------------------- DELETE -----------------------
    @Override
    public void delete(String id) {
        LOGGER.info("Deleting item with id='{}'", id);

        boolean deleted = repo.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("Item not found");
        }

        LOGGER.info("Item deleted successfully with id='{}'", id);
    }

    // ----------------------- RATING -----------------------
    @Override
    public ItemRS rate(String id, int stars) {
        LOGGER.info("Rating item with id='{}' with {} stars", id, stars);

        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        item.updateRating(stars);

        Item saved = repo.save(item);
        LOGGER.info("Item rated successfully with id='{}'", id);
        return ItemMapper.toRS(saved, clock.instant());
    }

    // ----------------------- DISCOUNT -----------------------
    @Override
    public ItemRS applyDiscount(String id, ApplyDiscountRQ rq) {
        LOGGER.info("Applying discount to item with id='{}', type={}, value={}", id, rq.type(), rq.value());

        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        DiscountType type = parseDiscountType(rq.type());

        if (type == DiscountType.PERCENT && (rq.value() < 0 || rq.value() > 100)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT", "percent 0..100");
        }
        if (type == DiscountType.AMOUNT && rq.value() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT", "amount must be >= 0");
        }

        Discount discount = new Discount(
                type,
                rq.value(),
                rq.label(),
                rq.startsAt(),
                rq.endsAt()
        );
        item.applyDiscount(discount);

        Item saved = repo.save(item);
        LOGGER.info("Discount applied successfully to item with id='{}'", id);
        return ItemMapper.toRS(saved, clock.instant());
    }

    @Override
    public ItemRS clearDiscount(String id) {
        LOGGER.info("Clearing discount from item with id='{}'", id);

        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        item.clearDiscount();

        Item saved = repo.save(item);
        LOGGER.info("Discount cleared successfully from item with id='{}'", id);
        return ItemMapper.toRS(saved, clock.instant());
    }

    // ----------------------- Helpers -----------------------


    private static DiscountType parseDiscountType(String raw) {
        List<String> allowed = Arrays.stream(DiscountType.values()).map(Enum::name).toList();
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ENUM_INVALID",
                    "Field 'type' must be one of: " + String.join(", ", allowed));
        }
        try {
            return DiscountType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ENUM_INVALID",
                    "Field 'type' must be one of: " + String.join(", ", allowed));
        }
    }
}
