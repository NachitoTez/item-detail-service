package com.ignacioramirez.itemDetailService.service;

import com.ignacioramirez.itemDetailService.domain.*;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.PriceRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemMapperTest {

    // ---------- toNewDomain ----------

    @Test
    @DisplayName("toNewDomain: normaliza currency/condition en mayúsculas y mapea campos básicos")
    void toNewDomain_normalizesAndMaps() {
        CreateItemRQ rq = new CreateItemRQ(
                "SKU-1",
                "Lightsaber",
                "Blue saber",
                new PriceRQ("ars", new BigDecimal("1000.00")),
                5,
                "OBI-WAN",
                "new",
                true,
                null,
                null
        );

        Item item = ItemMapper.toNewDomain(rq);

        assertNotNull(item);
        assertEquals("SKU-1", item.getSku());
        assertEquals("Lightsaber", item.getTitle());
        assertEquals("Blue saber", item.getDescription());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(new BigDecimal("1000.00"), item.getBasePrice().amount());
        assertEquals(5, item.getStock());
        assertEquals("OBI-WAN", item.getSellerId());
        assertEquals(Condition.NEW, item.getCondition());
        assertTrue(item.isFreeShipping());
        assertTrue(item.getCategories().isEmpty());
        assertTrue(item.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("toNewDomain: mapea correctamente categories y attributes cuando están presentes")
    void toNewDomain_withCategoriesAndAttributes() {
        CreateItemRQ rq = new CreateItemRQ(
                "SKU-2",
                "Item",
                "Description",
                new PriceRQ("USD", new BigDecimal("99.99")),
                10,
                "SELLER-123",
                "USED",
                false,
                List.of("Electronics", "Gaming"),
                Map.of("brand", "Sony", "color", "Black")
        );

        Item item = ItemMapper.toNewDomain(rq);

        assertEquals(List.of("Electronics", "Gaming"), item.getCategories());
        assertEquals(Map.of("brand", "Sony", "color", "Black"), item.getAttributes());
        assertEquals("USD", item.getBasePrice().currency());
        assertEquals(Condition.USED, item.getCondition());
        assertFalse(item.isFreeShipping());
    }

    // ---------- applyUpdate ----------

    @Test
    @DisplayName("applyUpdate: actualiza título, descripción, precio y stock cuando todos están presentes")
    void applyUpdate_updatesAllFields() {
        Item item = new ItemBuilder()
                .id("ID1")
                .sku("SKU-1")
                .title("Old title")
                .description("Old desc")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(1)
                .sellerId("SELLER")
                .condition(Condition.USED)
                .freeShipping(false)
                .categories(List.of("Cat1"))
                .attributes(Map.of("k","v"))
                .build();

        UpdateItemRQ rq = new UpdateItemRQ(
                "New title",
                "New desc",
                new PriceRQ("ARS", new BigDecimal("250.50")),
                7
        );

        ItemMapper.applyUpdate(item, rq);

        assertEquals("New title", item.getTitle());
        assertEquals("New desc", item.getDescription());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(new BigDecimal("250.50"), item.getBasePrice().amount());
        assertEquals(7, item.getStock());
    }

    @Test
    @DisplayName("applyUpdate: actualiza solo el título cuando es el único campo presente")
    void applyUpdate_updatesOnlyTitle() {
        Item item = new ItemBuilder()
                .id("ID1")
                .sku("SKU-1")
                .title("Old title")
                .description("Old desc")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(5)
                .sellerId("SELLER")
                .build();

        UpdateItemRQ rq = new UpdateItemRQ("New title", null, null, null);

        ItemMapper.applyUpdate(item, rq);

        assertEquals("New title", item.getTitle());
        // Los demás campos no cambian
        assertEquals("Old desc", item.getDescription());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(new BigDecimal("100.00"), item.getBasePrice().amount());
        assertEquals(5, item.getStock());
    }

    @Test
    @DisplayName("applyUpdate: actualiza solo la descripción cuando es el único campo presente")
    void applyUpdate_updatesOnlyDescription() {
        Item item = new ItemBuilder()
                .id("ID1")
                .sku("SKU-1")
                .title("Title")
                .description("Old desc")
                .price(new Price("USD", new BigDecimal("50.00")))
                .stock(3)
                .sellerId("SELLER")
                .build();

        UpdateItemRQ rq = new UpdateItemRQ(null, "New description", null, null);

        ItemMapper.applyUpdate(item, rq);

        assertEquals("New description", item.getDescription());
        // Los demás campos no cambian
        assertEquals("Title", item.getTitle());
        assertEquals("USD", item.getBasePrice().currency());
        assertEquals(new BigDecimal("50.00"), item.getBasePrice().amount());
        assertEquals(3, item.getStock());
    }

    @Test
    @DisplayName("applyUpdate: actualiza solo el precio cuando es el único campo presente")
    void applyUpdate_updatesOnlyPrice() {
        Item item = new ItemBuilder()
                .id("ID1")
                .sku("SKU-1")
                .title("Title")
                .description("Desc")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(10)
                .sellerId("SELLER")
                .build();

        UpdateItemRQ rq = new UpdateItemRQ(null, null, new PriceRQ("USD", new BigDecimal("75.00")), null);

        ItemMapper.applyUpdate(item, rq);

        assertEquals("USD", item.getBasePrice().currency());
        assertEquals(new BigDecimal("75.00"), item.getBasePrice().amount());
        // Los demás campos no cambian
        assertEquals("Title", item.getTitle());
        assertEquals("Desc", item.getDescription());
        assertEquals(10, item.getStock());
    }

    @Test
    @DisplayName("applyUpdate: actualiza solo el stock cuando es el único campo presente")
    void applyUpdate_updatesOnlyStock() {
        Item item = new ItemBuilder()
                .id("ID1")
                .sku("SKU-1")
                .title("Title")
                .description("Desc")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(5)
                .sellerId("SELLER")
                .build();

        UpdateItemRQ rq = new UpdateItemRQ(null, null, null, 20);

        ItemMapper.applyUpdate(item, rq);

        assertEquals(20, item.getStock());
        // Los demás campos no cambian
        assertEquals("Title", item.getTitle());
        assertEquals("Desc", item.getDescription());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(new BigDecimal("100.00"), item.getBasePrice().amount());
    }

    @Test
    @DisplayName("applyUpdate: no hace cambios cuando todos los campos son null")
    void applyUpdate_noChangesWhenAllFieldsNull() {
        Item item = new ItemBuilder()
                .id("ID1")
                .sku("SKU-1")
                .title("Original title")
                .description("Original desc")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(5)
                .sellerId("SELLER")
                .build();

        UpdateItemRQ rq = new UpdateItemRQ(null, null, null, null);

        ItemMapper.applyUpdate(item, rq);

        // Nada debería haber cambiado
        assertEquals("Original title", item.getTitle());
        assertEquals("Original desc", item.getDescription());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(new BigDecimal("100.00"), item.getBasePrice().amount());
        assertEquals(5, item.getStock());
    }

    @Test
    @DisplayName("applyUpdate: actualiza múltiples campos pero no todos")
    void applyUpdate_updatesPartialFields() {
        Item item = new ItemBuilder()
                .id("ID1")
                .sku("SKU-1")
                .title("Old title")
                .description("Old desc")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(5)
                .sellerId("SELLER")
                .build();

        // Solo actualiza título y stock
        UpdateItemRQ rq = new UpdateItemRQ("New title", null, null, 15);

        ItemMapper.applyUpdate(item, rq);

        assertEquals("New title", item.getTitle());
        assertEquals(15, item.getStock());
        // Estos no cambian
        assertEquals("Old desc", item.getDescription());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(new BigDecimal("100.00"), item.getBasePrice().amount());
    }

    // ---------- toRS ----------

    @Test
    @DisplayName("toRS: sin descuento -> currentPrice == basePrice, hasActiveDiscount=false, discount=null")
    void toRS_withoutDiscount() {
        Item item = new ItemBuilder()
                .id("ID1")
                .sku("SKU-1")
                .title("T")
                .description("D")
                .price(new Price("ARS", new BigDecimal("999.99")))
                .stock(3)
                .sellerId("SELLER-1")
                .pictures(List.of(new Picture("http://img/1.jpg", true, "main")))
                .condition(Condition.NEW)
                .freeShipping(true)
                .categories(List.of("A","B"))
                .attributes(Map.of("color","blue"))
                .build();

        Instant now = Instant.parse("2025-10-06T12:00:00Z");
        ItemRS rs = ItemMapper.toRS(item, now);

        assertNotNull(rs);
        assertEquals("ID1", rs.id());
        assertEquals("SKU-1", rs.sku());
        assertEquals("T", rs.title());
        assertEquals("D", rs.description());

        assertNotNull(rs.basePrice());
        assertEquals(new BigDecimal("999.99"), rs.basePrice().amount());
        assertEquals("ARS", rs.basePrice().currency());

        assertNotNull(rs.currentPrice());
        assertEquals(new BigDecimal("999.99"), rs.currentPrice().amount());
        assertEquals("ARS", rs.currentPrice().currency());

        assertFalse(rs.hasActiveDiscount());
        assertNull(rs.discount());

        assertEquals(3, rs.stock());
        assertEquals("SELLER-1", rs.sellerId());

        assertNotNull(rs.pictures());
        assertEquals(1, rs.pictures().size());
        PictureRS p0 = rs.pictures().getFirst();
        assertEquals("http://img/1.jpg", p0.url());
        assertTrue(p0.main());
        assertEquals("main", p0.alt());

        assertEquals(Condition.NEW, rs.condition());
        assertTrue(rs.freeShipping());
        assertEquals(List.of("A","B"), rs.categories());
        assertEquals(Map.of("color","blue"), rs.attributes());
    }

    @Test
    @DisplayName("toRS: con descuento PERCENT activo -> currentPrice aplica porcentaje y DiscountRS mapeado")
    void toRS_withPercentDiscountActive() {
        Price base = new Price("ARS", new BigDecimal("1000.00"));
        Instant starts = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant ends   = Instant.now().plus(1, ChronoUnit.DAYS);
        Discount discount = new Discount(DiscountType.PERCENT, 10L, "10% OFF", starts, ends);

        Item item = new ItemBuilder()
                .id("ID2")
                .sku("SKU-2")
                .title("T")
                .description("D")
                .price(base)
                .discount(discount)
                .stock(10)
                .sellerId("SELLER-2")
                .pictures(List.of(
                        new Picture("http://img/2.jpg", false, "secondary"),
                        new Picture("http://img/3.jpg", true, "main")
                ))
                .condition(Condition.REFURBISHED)
                .freeShipping(false)
                .categories(List.of())
                .attributes(Map.of())
                .build();

        Instant now = Instant.now();
        ItemRS rs = ItemMapper.toRS(item, now);

        assertEquals(new BigDecimal("1000.00"), rs.basePrice().amount());
        assertEquals("ARS", rs.basePrice().currency());

        // 10% OFF => 900.00
        assertEquals(new BigDecimal("900.00"), rs.currentPrice().amount());
        assertEquals("ARS", rs.currentPrice().currency());

        // Descuento
        assertTrue(rs.hasActiveDiscount());
        assertNotNull(rs.discount());
        assertEquals("PERCENT", rs.discount().type());
        assertEquals(10L, rs.discount().value());
        assertEquals("10% OFF", rs.discount().label());
        assertEquals(starts, rs.discount().startsAt());
        assertEquals(ends, rs.discount().endsAt());
        assertTrue(rs.discount().active());

        assertEquals(2, rs.pictures().size());
        assertEquals(Condition.REFURBISHED, rs.condition());
    }

    @Test
    @DisplayName("toRS: con descuento AMOUNT activo -> currentPrice resta monto y no baja de 0")
    void toRS_withAmountDiscountActive() {
        Price base = new Price("USD", new BigDecimal("50.00"));
        Instant starts = Instant.parse("2025-10-05T00:00:00Z");
        Instant ends   = Instant.parse("2025-10-10T00:00:00Z");
        Discount discount = new Discount(DiscountType.AMOUNT, 60L, "Save $60", starts, ends); // excede base

        Item item = new ItemBuilder()
                .id("ID3")
                .sku("SKU-3")
                .title("Title")
                .description("Desc")
                .price(base)
                .discount(discount)
                .stock(1)
                .sellerId("S")
                .condition(Condition.USED)
                .freeShipping(false)
                .categories(List.of())
                .attributes(Map.of())
                .build();

        ItemRS rs = ItemMapper.toRS(item, Instant.parse("2025-10-06T00:00:00Z"));

        assertEquals(new BigDecimal("0.00"), rs.currentPrice().amount());
        assertEquals("USD", rs.currentPrice().currency());

        assertTrue(rs.hasActiveDiscount());
        assertTrue(rs.discount().active());
        assertEquals("AMOUNT", rs.discount().type());
        assertEquals(60L, rs.discount().value());
    }

    @Test
    @DisplayName("toRS: descuento programado FUTURO no se expone (variante A)")
    void toRS_futureDiscount_notExposed() {
        Price base = new Price("USD", new BigDecimal("50.00"));
        Item item = new ItemBuilder()
                .id("FUT-1").sku("SKU-F").title("T").description("D")
                .price(base).stock(1).sellerId("S").build();

        Instant starts = Instant.parse("2025-10-10T00:00:00Z"); // futuro
        Instant ends   = Instant.parse("2025-10-12T00:00:00Z");
        item.applyDiscount(new Discount(DiscountType.PERCENT, 20L, "20% soon", starts, ends));

        Instant now = Instant.parse("2025-10-06T00:00:00Z");
        ItemRS rs = ItemMapper.toRS(item, now);

        assertFalse(rs.hasActiveDiscount());
        assertNull(rs.discount());
        assertEquals(base.amount(), rs.currentPrice().amount());
        assertEquals(base.currency(), rs.currentPrice().currency());
    }

    @Test
    @DisplayName("toRS: descuento EXPIRADO no se expone (variante A)")
    void toRS_expiredDiscount_notExposed() {
        Price base = new Price("USD", new BigDecimal("80.00"));
        Item item = new ItemBuilder()
                .id("EXP-1").sku("SKU-E").title("T").description("D")
                .price(base).stock(1).sellerId("S").build();

        Instant starts = Instant.parse("2025-10-01T00:00:00Z");
        Instant ends   = Instant.parse("2025-10-02T00:00:00Z"); // expirado
        item.applyDiscount(new Discount(DiscountType.AMOUNT, 10L, "Save 10", starts, ends));

        Instant now = Instant.parse("2025-10-06T00:00:00Z");
        ItemRS rs = ItemMapper.toRS(item, now);

        assertFalse(rs.hasActiveDiscount());
        assertNull(rs.discount());
        assertEquals(base.amount(), rs.currentPrice().amount());
    }

    @Test
    @DisplayName("mapDiscount: mapea todos los campos y 'active' según now (helper)")
    void mapDiscount_mapsFields_andActiveFlag() throws Exception {
        var m = ItemMapper.class.getDeclaredMethod(
                "mapDiscount", Discount.class, Instant.class);
        m.setAccessible(true);

        Instant starts = Instant.parse("2025-10-05T00:00:00Z");
        Instant ends   = Instant.parse("2025-10-10T00:00:00Z");
        Discount d = new Discount(DiscountType.AMOUNT, 123L, "Save 123", starts, ends);

        var rsActive = (DiscountRS) m.invoke(null, d, Instant.parse("2025-10-06T00:00:00Z"));
        assertEquals("AMOUNT", rsActive.type());
        assertEquals(123L, rsActive.value());
        assertEquals("Save 123", rsActive.label());
        assertEquals(starts, rsActive.startsAt());
        assertEquals(ends, rsActive.endsAt());
        assertTrue(rsActive.active());

        var rsInactive = (DiscountRS) m.invoke(null, d, Instant.parse("2025-10-11T00:00:00Z"));
        assertFalse(rsInactive.active());
    }
}