package com.ignacioramirez.itemDetailService.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

import static com.ignacioramirez.itemDetailService.service.utils.Texts.normalizeTitle;

public class Item {

    private final String id;
    private String title;
    private final String titleNormalized;
    private String description;
    @JsonProperty("price")
    private Price price;
    private Discount discount;
    private int stock;
    private final String sellerId;
    private final List<Picture> pictures;
    private Rating rating;
    private final Condition condition;
    private boolean freeShipping;
    private final List<String> categories;
    private final Map<String, String> attributes;

    @JsonCreator
    Item(@JsonProperty("id") String id,
         @JsonProperty("title") String title,
         @JsonProperty("description") String description,
         @JsonProperty("price") Price price,
         @JsonProperty("discount") Discount discount,
         @JsonProperty("stock") int stock,
         @JsonProperty("sellerId") String sellerId,
         @JsonProperty("pictures") List<Picture> pictures,
         @JsonProperty("rating") Rating rating,
         @JsonProperty("condition") Condition condition,
         @JsonProperty("freeShipping") boolean freeShipping,
         @JsonProperty("categories") List<String> categories,
         @JsonProperty("attributes") Map<String, String> attributes) {
        this.id = id;
        this.title = title;
        this.titleNormalized = normalizeTitle(title);
        this.description = description;
        this.price = price;
        this.discount = discount;
        this.stock = stock;
        this.sellerId = sellerId;
        this.pictures = pictures != null ? new ArrayList<>(pictures) : new ArrayList<>();
        this.rating = rating != null ? rating : Rating.empty();
        this.condition = condition != null ? condition : Condition.NEW;
        this.freeShipping = freeShipping;
        this.categories = categories != null ? new ArrayList<>(categories) : new ArrayList<>();
        this.attributes = attributes != null ? new LinkedHashMap<>(attributes) : new LinkedHashMap<>();
    }

    @JsonIgnore
    public Price getBasePrice() { return price; }

    public Price getCurrentPrice(Instant now) {
        if (discount == null || !discount.isActive(now)) {
            return price;
        }

        BigDecimal base = price.amount();
        BigDecimal current;

        switch (discount.type()) {
            case PERCENT -> {
                BigDecimal percent = BigDecimal.valueOf(discount.value())
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                BigDecimal discountAmount = base.multiply(percent);
                current = base.subtract(discountAmount);
            }
            case AMOUNT -> {
                BigDecimal discountAmount = BigDecimal.valueOf(discount.value());
                current = base.subtract(discountAmount);
            }
            default -> throw new IllegalStateException("Unexpected discount type: " + discount.type());
        }

        if (current.compareTo(BigDecimal.ZERO) < 0) {
            current = BigDecimal.ZERO;
        }
        current = current.setScale(2, RoundingMode.HALF_UP);

        return price.withAmount(current);
    }


    public boolean hasActiveDiscount(Instant now) {
        return discount != null && discount.isActive(now);
    }

    public void changeTitle(String newTitle) {
        if (newTitle == null || newTitle.isBlank()) throw new IllegalArgumentException("title required");
        this.title = newTitle;
    }

    public void changeDescription(String newDesc) {
        if (newDesc == null || newDesc.isBlank()) throw new IllegalArgumentException("description required");
        this.description = newDesc;
    }

    public void changeBasePrice(Price newPrice) {
        Objects.requireNonNull(newPrice, "price");
        this.price = newPrice;
    }

    public void applyDiscount(Discount d) {
        this.discount = d;
    }

    public void clearDiscount() {
        this.discount = null;
    }

    public void setStock(int newStock) {
        if (newStock < 0) throw new IllegalArgumentException("stock >= 0");
        this.stock = newStock;
    }

    public void incrementStock(int delta) {
        int next = this.stock + delta;
        if (next < 0) throw new IllegalStateException("stock cannot be negative");
        this.stock = next;
    }

    public void updateRating(int stars) {
        this.rating = Objects.requireNonNull(this.rating, "rating").addVote(stars);
    }

    public void addPicture(Picture picture) {
        Objects.requireNonNull(picture, "picture required");
        if (picture.main()) {
            pictures.replaceAll(p -> new Picture(p.url(), false, p.alt()));
        }
        pictures.add(picture);
    }

    public boolean removePictureByUrl(String url) {
        Objects.requireNonNull(url, "url");
        return pictures.removeIf(p -> url.equals(p.url()));
    }

    public boolean setMainPicture(String url) {
        Objects.requireNonNull(url, "url");
        boolean found = false;
        for (int i = 0; i < pictures.size(); i++) {
            var p = pictures.get(i);
            boolean makeMain = url.equals(p.url());
            if (makeMain) found = true;
            pictures.set(i, new Picture(p.url(), makeMain, p.alt()));
        }
        return found;
    }

    public void addCategory(String category) {
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category required");
        categories.add(category);
    }

    public boolean removeCategory(String category) {
        return categories.remove(category);
    }

    public void replaceCategories(List<String> newCategories) {
        categories.clear();
        if (newCategories != null) {
            for (var c : newCategories) addCategory(c);
        }
    }

    public void putAttribute(String key, String value) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key required");
        if (value == null || value.isBlank()) { attributes.remove(key); return; }
        attributes.put(key, value);
    }

    public void removeAttribute(String key) {
        if (key != null) attributes.remove(key);
    }

    public void clearAttributes() {
        attributes.clear();
    }

    public void setFreeShipping(boolean free) {
        this.freeShipping = free;
    }




    // --------- Getters básicos ----------
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    @JsonIgnore
    public String getTitleNormalized() { return titleNormalized; }

    @JsonIgnore
    public Optional<Discount> getDiscountOptional() {
        return Optional.ofNullable(discount);
    }
    @JsonIgnore
    public Optional<Discount> getActiveDiscount(Instant now) {
        return Optional.ofNullable(discount).filter(d -> d.isActive(now));
    }
    public int getStock() { return stock; }
    public String getSellerId() { return sellerId; }
    public List<Picture> getPictures() { return Collections.unmodifiableList(pictures); }
    public Rating getRating() { return rating; }
    public Condition getCondition() { return condition; }
    public boolean isFreeShipping() { return freeShipping; }
    public List<String> getCategories() { return Collections.unmodifiableList(categories); }
    public Map<String, String> getAttributes() { return Collections.unmodifiableMap(attributes); }

    public void validate() {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(price, "price");
        if (stock < 0) throw new IllegalStateException("stock >= 0");
    }
}