package com.ignacioramirez.itemDetailService.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemBuilderTest {

    private static BigDecimal money(String num) {
        return new BigDecimal(num).setScale(2);
    }

    private ItemBuilder base() {
        return new ItemBuilder()
                .title("title")
                .description("desc")
                .price(new Price("ARS", BigDecimal.valueOf(10000)))
                .stock(1)
                .sellerId("SELLER-1");
    }

    @Test
    void build_minimalRequired_success() {
        var item = base().build();

        assertNotNull(item.getId());
        assertEquals("title", item.getTitle());
        assertEquals("desc", item.getDescription());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(0, item.getBasePrice().amount().compareTo(money("10000")));
        assertEquals(1, item.getStock());
        assertEquals("SELLER-1", item.getSellerId());
    }

    @Test
    void validations_requiredFields() {

        // title requerido
        assertThrows(IllegalArgumentException.class, () ->
                new ItemBuilder()
                        .description("d")
                        .price(new Price("ARS", BigDecimal.ONE)).stock(1).sellerId("S")
                        .build()
        );

        // description requerida
        assertThrows(IllegalArgumentException.class, () ->
                new ItemBuilder()
                        .title("t")
                        .price(new Price("ARS", BigDecimal.ONE)).stock(1).sellerId("S")
                        .build()
        );

        // price requerido
        assertThrows(NullPointerException.class, () ->
                new ItemBuilder()
                        .title("t").description("d")
                        .stock(1).sellerId("S")
                        .build()
        );

        // sellerId requerido
        assertThrows(NullPointerException.class, () ->
                new ItemBuilder()
                        .title("t").description("d")
                        .price(new Price("ARS", BigDecimal.ONE)).stock(1)
                        .build()
        );

        // stock no negativo
        assertThrows(IllegalArgumentException.class, () ->
                base().stock(-1).build()
        );
    }

    @Test
    void copiesDefensivas_deListasYMapas() {
        var pics = new ArrayList<>(List.of(new Picture("http://a", true, "alt")));
        var cats = new ArrayList<>(List.of("a", "b"));
        var attrs = Map.of("k", "v");

        var item = base()
                .pictures(pics)
                .categories(cats)
                .attributes(attrs)
                .build();

        pics.add(new Picture("http://b", false, "alt2"));
        cats.add("c");
        assertEquals(1, item.getPictures().size());
        assertEquals(List.of("a","b"), item.getCategories());
        assertEquals("v", item.getAttributes().get("k"));

        // Getters inmutables
        assertThrows(UnsupportedOperationException.class, () -> item.getPictures().add(new Picture("x", false, null)));
        assertThrows(UnsupportedOperationException.class, () -> item.getCategories().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> item.getAttributes().put("x", "y"));
    }

    @Test
    void optionalFields_discount_rating_condition_canBeSet() {
        var rating = new Rating(4.5, 10);
        var discount = new Discount(DiscountType.PERCENT, 25, "SALE", null, null);

        var item = base()
                .rating(rating)
                .discount(discount)
                .condition(Condition.USED)
                .freeShipping(true)
                .build();

        assertEquals(4.5, item.getRating().average());
        assertEquals(10, item.getRating().count());
        assertEquals(Condition.USED, item.getCondition());
        assertTrue(item.isFreeShipping());
        assertEquals(0, item.getCurrentPrice(java.time.Instant.now()).amount().compareTo(money("7500")));
    }

    @Test
    void nullables_ratingYCondition_seNormalizan() {
        var item = base()
                .rating(null)
                .condition(null)
                .build();

        assertEquals(0, item.getRating().count());
        assertEquals(0.0, item.getRating().average());
        assertEquals(Condition.NEW, item.getCondition());
    }

    // -------------------- TESTS ADICIONALES --------------------

    @Test
    void defaults_whenNotProvided() {
        var item = base().build();

        assertEquals(Condition.NEW, item.getCondition());
        assertFalse(item.isFreeShipping());
        assertEquals(0, item.getRating().count());
        assertEquals(0.0, item.getRating().average());
        assertTrue(item.getPictures().isEmpty());
        assertTrue(item.getCategories().isEmpty());
        assertTrue(item.getAttributes().isEmpty());
    }

    @Test
    void customId_isHonored_and_randomIdGeneratedOtherwise() {
        var custom = base().id("ITEM-123").build();
        assertEquals("ITEM-123", custom.getId());

        var random = base().build();
        assertNotNull(random.getId());
        assertFalse(random.getId().isBlank());
    }

    @Test
    void reuseBuilder_keepsSameId_documental() {
        var builder = base();
        var item1 = builder.build();
        var item2 = builder.title("new title").build();

        assertEquals(item1.getId(), item2.getId());
    }

    @Test
    void mutatingBuilderAfterBuild_doesNotAffectConstructedItem() {
        var builder = base()
                .pictures(List.of(new Picture("http://a", true, "a")))
                .categories(List.of("A"))
                .attributes(Map.of("k", "v"));

        var item = builder.build();

        builder.pictures(List.of(new Picture("http://b", true, "b")));
        builder.categories(List.of("B"));
        builder.attributes(Map.of("x", "y"));
        builder.freeShipping(true);

        assertEquals(1, item.getPictures().size());
        assertEquals("http://a", item.getPictures().getFirst().url());
        assertEquals(List.of("A"), item.getCategories());
        assertEquals("v", item.getAttributes().get("k"));
        assertFalse(item.isFreeShipping());
    }

    @Test
    void setters_withNullCollections_throwImmediately_currentPolicy() {
        assertThrows(NullPointerException.class, () -> base().pictures(null));
        assertThrows(NullPointerException.class, () -> base().categories(null));
        assertThrows(NullPointerException.class, () -> base().attributes(null));
    }

    @Test
    void defensiveCopy_happensAtSetterTime_notOnlyAtBuild() {
        var pics = new ArrayList<>(List.of(new Picture("http://a", true, "a")));
        var cats = new ArrayList<>(List.of("A"));
        var attrs = new java.util.LinkedHashMap<String,String>();
        attrs.put("k", "v");

        var builder = base()
                .pictures(pics)
                .categories(cats)
                .attributes(attrs);

        pics.add(new Picture("http://b", false, "b"));
        cats.add("B");
        attrs.put("x", "y");

        var item = builder.build();

        assertEquals(1, item.getPictures().size());
        assertEquals(List.of("A"), item.getCategories());
        assertEquals(Map.of("k", "v"), item.getAttributes());
    }
}
