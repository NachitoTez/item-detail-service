package com.ignacioramirez.itemDetailService.domain;

import java.util.*;

public class ItemBuilder {
    // Requeridos
    private String id = UUID.randomUUID().toString();
    private String sku;
    private String title;
    private String description;
    private Price price;
    private int stock;
    private String sellerId;

    // Opcionales
    private Discount discount;
    private List<Picture> pictures = new ArrayList<>();
    private Rating rating = Rating.empty();
    private Condition condition = Condition.NEW;
    private boolean freeShipping = false;
    private List<String> categories = new ArrayList<>();
    private Map<String,String> attributes = new LinkedHashMap<>();

    // --------- setters fluidos ----------
    public ItemBuilder id(String id) { this.id = id; return this; }
    public ItemBuilder sku(String sku) { this.sku = sku; return this; }
    public ItemBuilder title(String title) { this.title = title; return this; }
    public ItemBuilder description(String description) { this.description = description; return this; }
    public ItemBuilder price(Price price) { this.price = price; return this; }
    public ItemBuilder stock(int stock) { this.stock = stock; return this; }
    public ItemBuilder sellerId(String sellerId) { this.sellerId = sellerId; return this; }

    public ItemBuilder discount(Discount discount) { this.discount = discount; return this; }
    public ItemBuilder pictures(List<Picture> pictures) { this.pictures = new ArrayList<>(pictures); return this; }
    public ItemBuilder rating(Rating rating) { this.rating = rating; return this; }
    public ItemBuilder condition(Condition condition) { this.condition = condition; return this; }
    public ItemBuilder freeShipping(boolean free) { this.freeShipping = free; return this; }
    public ItemBuilder categories(List<String> categories) { this.categories = new ArrayList<>(categories); return this; }
    public ItemBuilder attributes(Map<String,String> attributes) { this.attributes = new LinkedHashMap<>(attributes); return this; }

    // --------- build con validaciones ----------
    public Item build() {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description required");
        Objects.requireNonNull(price, "price");
        if (stock < 0) throw new IllegalArgumentException("stock >= 0");
        Objects.requireNonNull(sellerId, "sellerId");

        var pics = (pictures == null) ? new ArrayList<Picture>() : new ArrayList<>(pictures);
        var cats = (categories == null) ? new ArrayList<String>() : new ArrayList<>(categories);
        var attrs = (attributes == null) ? new LinkedHashMap<String,String>() : new LinkedHashMap<>(attributes);
        var rat = (rating == null) ? new Rating(0.0, 0) : rating;
        var cond = (condition == null) ? Condition.NEW : condition;

        return new Item(
                id, sku, title, description, price, discount, stock, sellerId,
                pics, rat, cond, freeShipping, cats, attrs
        );
    }
}
