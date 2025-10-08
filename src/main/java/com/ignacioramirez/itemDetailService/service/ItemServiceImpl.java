package com.ignacioramirez.itemDetailService.service;

import com.ignacioramirez.itemDetailService.domain.*;
import com.ignacioramirez.itemDetailService.dto.items.request.ApplyDiscountRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.exceptions.ConflictException;
import com.ignacioramirez.itemDetailService.exceptions.NotFoundException;
import com.ignacioramirez.itemDetailService.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ItemServiceImpl implements ItemService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemServiceImpl.class);

    private final ItemRepository repo;
    private final Clock clock;

    public ItemServiceImpl(ItemRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    public ItemRS create(CreateItemRQ rq) {
        LOGGER.info("Creating item with sku='{}'", rq.sku());

        repo.findBySku(rq.sku()).ifPresent(existing -> {
            throw new ConflictException("SKU already exists");
        });
        Item item = ItemMapper.toNewDomain(rq);
        item.validate();
        Item saved = repo.save(item);

        LOGGER.info("Item created successfully with id='{}'", saved.getId());
        return ItemMapper.toRS(saved, clock.instant());
    }

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

    @Override
    public ItemRS update(String id, UpdateItemRQ rq) {
        LOGGER.info("Updating item with id='{}'", id);

        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        ItemMapper.applyUpdate(item, rq);
        item.validate();
        Item saved = repo.save(item);

        LOGGER.info("Item updated successfully with id='{}'", id);
        return ItemMapper.toRS(saved, clock.instant());
    }

    @Override
    public void delete(String id) {
        LOGGER.info("Deleting item with id='{}'", id);

        boolean deleted = repo.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("Item not found");
        }

        LOGGER.info("Item deleted successfully with id='{}'", id);
    }

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

    @Override
    public ItemRS applyDiscount(String id, ApplyDiscountRQ rq) {
        LOGGER.info("Applying discount to item with id='{}', type={}, value={}", id, rq.type(), rq.value());

        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        Discount discount = new Discount(
                DiscountType.valueOf(rq.type()),
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
}