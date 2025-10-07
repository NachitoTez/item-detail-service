package com.ignacioramirez.itemDetailService.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemDomainTest {

    // Helper para crear montos con scale=2
    private static BigDecimal money(String num) {
        return new BigDecimal(num).setScale(2);
    }

    // ------- Builders de conveniencia -------
    private static Item newItem() {
        return new ItemBuilder()
                .id("ID")
                .sku("SKU-1")
                .title("t")
                .description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1)
                .sellerId("S")
                .build();
    }

    private static Item newItemWithPictures(String... urls) {
        Item item = newItem();
        for (int i = 0; i < urls.length; i++) {
            item.addPicture(new Picture(urls[i], i == 0, "alt" + i));
        }
        return item;
    }

    // ------- Current price / Discount -------
    @Test
    void currentPrice_percentDiscount() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .build();

        item.applyDiscount(new Discount(DiscountType.PERCENT, 25, null, null, null));
        var curr = item.getCurrentPrice(Instant.now());

        assertEquals(0, curr.amount().compareTo(money("7500")));
        assertEquals("ARS", curr.currency());
    }

    @Test
    void currentPrice_amountDiscount_notBelowZero() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("5000")))
                .stock(1).sellerId("S")
                .build();

        item.applyDiscount(new Discount(DiscountType.AMOUNT, 6000, null, null, null));
        var curr = item.getCurrentPrice(Instant.now());

        assertEquals(0, curr.amount().compareTo(money("0")));
    }

    @Test
    void rating_updatesAverageAndCount() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("5000")))
                .stock(1).sellerId("S")
                .build();

        item.updateRating(5);
        item.updateRating(3);
        assertEquals(2, item.getRating().count());
        assertTrue(item.getRating().average() >= 3.9 && item.getRating().average() <= 4.1); // ~4.0
    }

    @Test
    void discount_inactiveBeforeStart_and_afterEnd() {
        var now = Instant.parse("2025-01-01T00:00:00Z");

        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .build();

        var startsTomorrow = now.plusSeconds(86400);
        item.applyDiscount(new Discount(DiscountType.PERCENT, 10, null, startsTomorrow, null));
        assertEquals(0, item.getCurrentPrice(now).amount().compareTo(money("10000")));
        assertFalse(item.hasActiveDiscount(now));

        var endsYesterday = now.minusSeconds(86400);
        item.applyDiscount(new Discount(DiscountType.PERCENT, 10, null, null, endsYesterday));
        assertEquals(0, item.getCurrentPrice(now).amount().compareTo(money("10000")));
        assertFalse(item.hasActiveDiscount(now));
    }

    @Test
    void discount_activeOnStartAndEndBoundaries() {
        var start = Instant.parse("2025-01-01T00:00:00Z");
        var end = Instant.parse("2025-01-02T00:00:00Z");

        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .build();

        item.applyDiscount(new Discount(DiscountType.PERCENT, 10, null, start, end));

        assertEquals(0, item.getCurrentPrice(start).amount().compareTo(money("9000")));
        assertTrue(item.hasActiveDiscount(start));

        assertEquals(0, item.getCurrentPrice(end).amount().compareTo(money("9000")));
        assertTrue(item.hasActiveDiscount(end));
    }

    @Test
    void percent_zeroAndHundred() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .build();

        item.applyDiscount(new Discount(DiscountType.PERCENT, 0, null, null, null));
        assertEquals(0, item.getCurrentPrice(Instant.now()).amount().compareTo(money("10000")));

        item.applyDiscount(new Discount(DiscountType.PERCENT, 100, null, null, null));
        assertEquals(0, item.getCurrentPrice(Instant.now()).amount().compareTo(money("0")));
    }

    @Test
    void percent_outOfRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new Discount(DiscountType.PERCENT, 101, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Discount(DiscountType.PERCENT, -1, null, null, null));
    }

    @Test
    void amount_negative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new Discount(DiscountType.AMOUNT, -1, null, null, null));
    }

    @Test
    void amount_equalToBase_becomesZero() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .build();

        item.applyDiscount(new Discount(DiscountType.AMOUNT, 10000, null, null, null));
        assertEquals(0, item.getCurrentPrice(Instant.now()).amount().compareTo(money("0")));
    }

    @Test
    void clearDiscount_restoresBasePrice() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .build();

        item.applyDiscount(new Discount(DiscountType.PERCENT, 50, null, null, null));
        assertEquals(0, item.getCurrentPrice(Instant.now()).amount().compareTo(money("5000")));
        item.clearDiscount();
        assertEquals(0, item.getCurrentPrice(Instant.now()).amount().compareTo(money("10000")));
    }

    // ------- Stock & titles -------
    @Test
    void stock_validations() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .build();

        assertThrows(IllegalArgumentException.class, () -> item.setStock(-1));
        assertThrows(IllegalStateException.class, () -> item.incrementStock(-5));
    }

    @Test
    void titleAndDescription_validations() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .build();

        assertThrows(IllegalArgumentException.class, () -> item.changeTitle(""));
        assertThrows(IllegalArgumentException.class, () -> item.changeDescription("  "));
    }

    // ------- Collections inmutables -------
    @Test
    void collections_areUnmodifiable() {
        var item = new ItemBuilder()
                .id("1").sku("SKU-1").title("t").description("d")
                .price(new Price("ARS", money("10000")))
                .stock(1).sellerId("S")
                .categories(List.of("a", "b"))
                .attributes(Map.of("k", "v"))
                .sellerId("S")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> item.getCategories().add("c"));
        assertThrows(UnsupportedOperationException.class, () -> item.getAttributes().put("x", "y"));
    }

    @Test
    void hasActiveDiscount_consistentWithCurrentPrice() {
        var item = newItem();

        assertFalse(item.hasActiveDiscount(Instant.now()));
        assertEquals(0, item.getBasePrice().amount().compareTo(item.getCurrentPrice(Instant.now()).amount()));
    }

    // ------- Pictures -------
    @Test
    void addPicture_whenMainTrue_clearsPreviousMain() {
        var item = newItem();
        item.addPicture(new Picture("http://a", true, "a"));
        item.addPicture(new Picture("http://b", false, "b"));
        item.addPicture(new Picture("http://c", true, "c")); // esta debería quedar main

        var mains = item.getPictures().stream().filter(Picture::main).toList();
        assertEquals(1, mains.size());
        assertEquals("http://c", mains.getFirst().url());
    }

    @Test
    void removePictureByUrl_returnsTrueOnlyIfRemoved() {
        var item = newItemWithPictures("http://a", "http://b");
        assertTrue(item.removePictureByUrl("http://a"));
        assertFalse(item.removePictureByUrl("http://zzz"));
        assertEquals(1, item.getPictures().size());
        assertEquals("http://b", item.getPictures().getFirst().url());
    }

    @Test
    void setMainPicture_togglesMainAndReturnsFoundFlag() {
        var item = newItemWithPictures("http://a", "http://b");
        assertFalse(item.setMainPicture("http://zz"));
        assertTrue(item.setMainPicture("http://b"));

        var mains = item.getPictures().stream().filter(Picture::main).toList();
        assertEquals(1, mains.size());
        assertEquals("http://b", mains.getFirst().url());
        assertFalse(item.getPictures().getFirst().main());
    }

    @Test
    void addPicture_null_throws() {
        var item = newItem();
        assertThrows(NullPointerException.class, () -> item.addPicture(null));
    }

    @Test
    void removePictureByUrl_null_throws() {
        var item = newItem();
        assertThrows(NullPointerException.class, () -> item.removePictureByUrl(null));
    }

    @Test
    void setMainPicture_null_throws() {
        var item = newItem();
        assertThrows(NullPointerException.class, () -> item.setMainPicture(null));
    }

    // ------- Categories -------
    @Test
    void addCategory_valid_adds() {
        var item = newItem();
        item.addCategory("A");
        item.addCategory("B");
        assertEquals(List.of("A", "B"), item.getCategories());
    }

    @Test
    void addCategory_blankOrNull_throws() {
        var item = newItem();
        assertThrows(IllegalArgumentException.class, () -> item.addCategory(" "));
        assertThrows(IllegalArgumentException.class, () -> item.addCategory(null));
    }

    @Test
    void removeCategory_returnsIfRemoved() {
        var item = new ItemBuilder()
                .id("X").sku("SKU-X").title("t").description("d")
                .price(new Price("ARS", money("100")))
                .stock(1).sellerId("S")
                .categories(List.of("A", "B"))
                .build();

        assertTrue(item.removeCategory("A"));
        assertFalse(item.removeCategory("Z"));
        assertEquals(List.of("B"), item.getCategories());
    }

    @Test
    void replaceCategories_replacesAll_andValidatesEach() {
        var item = new ItemBuilder()
                .id("X").sku("SKU-X").title("t").description("d")
                .price(new Price("ARS", money("100")))
                .stock(1).sellerId("S")
                .categories(List.of("A"))
                .build();

        item.replaceCategories(List.of("C", "D"));
        assertEquals(List.of("C", "D"), item.getCategories());

        // si alguna es inválida, addCategory lanza
        assertThrows(IllegalArgumentException.class, () -> item.replaceCategories(List.of("OK", "  ")));
    }

    @Test
    void replaceCategories_withNull_clearsToEmpty() {
        var item = new ItemBuilder()
                .id("X").sku("SKU-X").title("t").description("d")
                .price(new Price("ARS", money("100")))
                .stock(1).sellerId("S")
                .categories(List.of("A", "B"))
                .build();

        item.replaceCategories(null);
        assertTrue(item.getCategories().isEmpty());
    }

    // ------- Attributes -------
    @Test
    void putAttribute_setsAndOverwrites() {
        var item = newItem();
        item.putAttribute("color", "red");
        assertEquals("red", item.getAttributes().get("color"));

        item.putAttribute("color", "blue"); // overwrite
        assertEquals("blue", item.getAttributes().get("color"));
    }

    @Test
    void putAttribute_blankValue_removesKey() {
        var item = new ItemBuilder()
                .id("X").sku("SKU-X").title("t").description("d")
                .price(new Price("ARS", money("100")))
                .stock(1).sellerId("S")
                .attributes(Map.of("k", "v"))
                .build();

        item.putAttribute("k", "  ");
        assertFalse(item.getAttributes().containsKey("k"));
    }
}
