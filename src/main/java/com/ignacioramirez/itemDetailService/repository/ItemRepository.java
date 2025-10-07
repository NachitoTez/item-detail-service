package com.ignacioramirez.itemDetailService.repository;

import com.ignacioramirez.itemDetailService.domain.Item;
import java.util.*;

public interface ItemRepository {
    Optional<Item> findById(String id);
    Optional<Item> findBySku(String sku);
    List<Item> findAll(int page, int size);
    Item save(Item item);
    boolean deleteById(String id);
    long count();
}
