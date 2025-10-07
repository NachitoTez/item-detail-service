package com.ignacioramirez.itemDetailService.controller;

import com.ignacioramirez.itemDetailService.dto.items.request.ApplyDiscountRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.service.ItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping(value = "/items", produces = "application/json")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<ItemRS> create(@Valid @RequestBody CreateItemRQ rq) {

        ItemRS rs = service.create(rq);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(rs.id())
                .toUri();
        return ResponseEntity.created(location).body(rs);
    }

    @GetMapping("/{id}")
    public ItemRS getById(@PathVariable String id) {
        return service.getById(id);
    }

    @GetMapping
    public List<ItemRS> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String sku
    ) {
        //TODO esto debería pasarlo al service
        if (sku != null && !sku.isBlank()) {
            return service.list(0, 1).stream()
                    .filter(item -> sku.equals(item.sku()))
                    .toList();
        }
        return service.list(page, size);
    }

    @PutMapping(path = "/{id}", consumes = "application/json")
    public ItemRS update(@PathVariable String id, @Valid @RequestBody UpdateItemRQ rq) {
        return service.update(id, rq);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/{id}/rating")
    public ItemRS rate(@PathVariable String id,
                       @RequestParam @Min(1) @Max(5) int stars) {
        return service.rate(id, stars);
    }

    @PostMapping(path = "/{id}/discount", consumes = "application/json")
    public ItemRS applyDiscount(@PathVariable String id, @Valid @RequestBody ApplyDiscountRQ rq) {
        return service.applyDiscount(id, rq);
    }

    @DeleteMapping("/{id}/discount")
    public ItemRS clearDiscount(@PathVariable String id) {
        return service.clearDiscount(id);
    }
}
