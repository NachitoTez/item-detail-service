package com.ignacioramirez.itemDetailService.service;


import com.ignacioramirez.itemDetailService.dto.items.request.ApplyDiscountRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;

import java.util.List;

public interface ItemService {
    ItemRS create(CreateItemRQ rq);
    ItemRS getById(String id);
    List<ItemRS> list(int page, int size);
    ItemRS update(String id, UpdateItemRQ rq);
    void delete(String id);
    ItemRS rate(String id, int stars);
    ItemRS applyDiscount(String id, ApplyDiscountRQ rq);
    ItemRS clearDiscount(String id);
}
