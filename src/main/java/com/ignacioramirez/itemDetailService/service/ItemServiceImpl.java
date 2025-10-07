package com.ignacioramirez.itemDetailService.service;

import com.ignacioramirez.itemDetailService.domain.*;
import com.ignacioramirez.itemDetailService.dto.items.request.ApplyDiscountRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.exceptions.ConflictException;
import com.ignacioramirez.itemDetailService.exceptions.NotFoundException;
import com.ignacioramirez.itemDetailService.repository.ItemRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemRepository repo;
    private final Clock clock;

    public ItemServiceImpl(ItemRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    public ItemRS create(CreateItemRQ rq) {
        repo.findBySku(rq.sku()).ifPresent(existing -> {
            throw new ConflictException("SKU already exists");
        });
        Item item = ItemMapper.toNewDomain(rq);
        item.validate();
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, clock.instant());
    }

    @Override
    public ItemRS getById(String id) {
        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        return ItemMapper.toRS(item, clock.instant());
    }

    @Override
    public List<ItemRS> list(int page, int size) {
        List<Item> items = repo.findAll(page, size);
        Instant now = clock.instant();
        return items.stream()
                .map(item -> ItemMapper.toRS(item, now))
                .toList();
    }

    @Override
    public ItemRS update(String id, UpdateItemRQ rq) {
        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        ItemMapper.applyUpdate(item, rq);
        item.validate();
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, clock.instant());
    }

    @Override
    public void delete(String id) {
        boolean deleted = repo.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("Item not found");
        }
    }

    @Override
    public ItemRS rate(String id, int stars) {
        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.updateRating(stars);
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, clock.instant());
    }

    @Override
    public ItemRS applyDiscount(String id, ApplyDiscountRQ rq) {
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
        return ItemMapper.toRS(saved, clock.instant());
    }

    @Override
    public ItemRS clearDiscount(String id) {
        Item item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        item.clearDiscount();
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, clock.instant());
    }
}