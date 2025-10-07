package com.ignacioramirez.itemDetailService.service;


import com.ignacioramirez.itemDetailService.domain.Discount;
import com.ignacioramirez.itemDetailService.domain.DiscountType;
import com.ignacioramirez.itemDetailService.domain.Item;
import com.ignacioramirez.itemDetailService.dto.items.request.ApplyDiscountRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.exceptions.ConflictException;
import com.ignacioramirez.itemDetailService.exceptions.NotFoundException;
import com.ignacioramirez.itemDetailService.repository.ItemRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemRepository repo;

    public ItemServiceImpl(ItemRepository repo) {
        this.repo = repo;
    }

    @Override
    public ItemRS create(CreateItemRQ rq) {
        repo.findBySku(rq.sku()).ifPresent(i -> { throw new ConflictException("SKU already exists"); });
        Item item = ItemMapper.toNewDomain(rq);
        item.validate();
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, Instant.now());
    }

    @Override
    public ItemRS getById(String id) {
        Item item = repo.findById(id).orElseThrow(() -> new NotFoundException("Item %s not found".formatted(id)));
        return ItemMapper.toRS(item, Instant.now());
    }

    @Override
    public List<ItemRS> list(int page, int size) {
        return repo.findAll(page, size).stream()
                .map(it -> ItemMapper.toRS(it, Instant.now()))
                .toList();
    }

    @Override
    public ItemRS update(String id, UpdateItemRQ rq) {
        Item item = repo.findById(id).orElseThrow(() -> new NotFoundException("Item not found"));
        ItemMapper.applyUpdate(item, rq);
        item.validate();
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, Instant.now());
    }

    @Override
    public void delete(String id) {
        if (!repo.deleteById(id)) throw new NotFoundException("Item not found");
    }

    @Override
    public ItemRS rate(String id, int stars) {
        Item item = repo.findById(id).orElseThrow(() -> new NotFoundException("Item not found"));
        item.updateRating(stars);
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, Instant.now());
    }

    @Override
    public ItemRS applyDiscount(String id, ApplyDiscountRQ rq) {
        Item item = repo.findById(id).orElseThrow(() -> new NotFoundException("Item not found"));
        Discount discount = new Discount(DiscountType.valueOf(rq.type()), rq.value(), rq.label(), rq.startsAt(), rq.endsAt());
        item.applyDiscount(discount);
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, Instant.now());
    }

    @Override
    public ItemRS clearDiscount(String id) {
        Item item = repo.findById(id).orElseThrow(() -> new NotFoundException("Item not found"));
        item.clearDiscount();
        Item saved = repo.save(item);
        return ItemMapper.toRS(saved, Instant.now());
    }
}
