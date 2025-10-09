package com.ignacioramirez.itemDetailService.service;

import com.ignacioramirez.itemDetailService.domain.*;
import com.ignacioramirez.itemDetailService.dto.items.request.ApplyDiscountRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.PriceRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.exceptions.ApiException;
import com.ignacioramirez.itemDetailService.exceptions.ConflictException;
import com.ignacioramirez.itemDetailService.exceptions.NotFoundException;
import com.ignacioramirez.itemDetailService.repository.ItemRepository;
import com.ignacioramirez.itemDetailService.service.utils.Texts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceImplTest {

    @Mock
    ItemRepository repo;

    @Mock
    Clock clock;

    ItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ItemServiceImpl(repo, clock);
    }

    // Helper de normalización (igual que en el service)
    private static String normalizeTitle(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("create: conflicto si (sellerId, titleNormalized) ya existe")
    void create_conflictWhenTitleExistsForSeller() {
        var rq = new CreateItemRQ(
                "T",
                "D",
                new PriceRQ("ARS", new BigDecimal("10.00")),
                1,
                "SELLER",
                "NEW",
                false,
                List.of(),
                Map.of()
        );

        String expectedNorm = normalizeTitle(rq.title());

        when(repo.findBySellerAndTitleNormalized("SELLER", expectedNorm))
                .thenReturn(Optional.of(new ItemBuilder()
                        .id("X").title("old").description("old")
                        .price(new Price("ARS", new BigDecimal("1.00")))
                        .stock(1).sellerId("SELLER").build()));

        assertThrows(ConflictException.class, () -> service.create(rq));

        verify(repo).findBySellerAndTitleNormalized("SELLER", expectedNorm);
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("create: guarda y retorna ItemRS")
    void create_ok() {
        var rq = new CreateItemRQ(
                "T",
                "D",
                new PriceRQ("ARS", new BigDecimal("10.00")),
                1,
                "SELLER",
                "NEW",
                true,
                List.of("A"),
                Map.of("k", "v")
        );

        String expectedNorm = normalizeTitle(rq.title());

        when(repo.findBySellerAndTitleNormalized("SELLER", expectedNorm))
                .thenReturn(Optional.empty());

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.<Item>getArgument(0));

        ItemRS rs = service.create(rq);

        verify(repo).findBySellerAndTitleNormalized("SELLER", expectedNorm);
        verify(repo).save(captor.capture());
        verifyNoMoreInteractions(repo);

        Item saved = captor.getValue();
        assertEquals("T", saved.getTitle());
        assertEquals("D", saved.getDescription());
        assertEquals("ARS", saved.getBasePrice().currency());
        assertEquals(new BigDecimal("10.00"), saved.getBasePrice().amount());
        assertEquals(1, saved.getStock());
        assertEquals("SELLER", saved.getSellerId());
        assertEquals(Condition.NEW, saved.getCondition());
        assertTrue(saved.isFreeShipping());

        assertNotNull(rs);
        assertEquals("T", rs.title());
        assertEquals("SELLER", rs.sellerId());
    }

    // ---------------- getById ----------------

    @Test
    @DisplayName("getById: retorna ItemRS cuando existe")
    void getById_ok() {
        var item = new ItemBuilder()
                .id("ID1").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("5.00")))
                .stock(1).sellerId("S").build();

        when(repo.findById("ID1")).thenReturn(Optional.of(item));

        ItemRS rs = service.getById("ID1");

        assertNotNull(rs);
        assertEquals("ID1", rs.id());
        assertEquals("T", rs.title());
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("getById: NotFound si no existe")
    void getById_notFound() {
        when(repo.findById("ID1")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getById("ID1"));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    // ---------------- list ----------------

    @Test
    @DisplayName("list: mapea cada Item a ItemRS")
    void list_ok() {
        var i1 = new ItemBuilder().id("I1").title("T1").description("D1")
                .price(new Price("ARS", new BigDecimal("1.00"))).stock(1).sellerId("S").build();
        var i2 = new ItemBuilder().id("I2").title("T2").description("D2")
                .price(new Price("ARS", new BigDecimal("2.00"))).stock(2).sellerId("S").build();

        when(repo.findAll(2, 5)).thenReturn(List.of(i1, i2));

        List<ItemRS> out = service.list(2, 5);

        assertEquals(2, out.size());
        assertEquals("I1", out.get(0).id());
        assertEquals("I2", out.get(1).id());
        verify(repo).findAll(2, 5);
        verifyNoMoreInteractions(repo);
    }

    // ---------------- update ----------------

    @Test
    @DisplayName("update: aplica cambios, valida, guarda y retorna ItemRS")
    void update_ok() {
        var item = new ItemBuilder()
                .id("ID1").title("Old").description("Old")
                .price(new Price("ARS", new BigDecimal("10.00")))
                .stock(1).sellerId("S").build();

        when(repo.findById("ID1")).thenReturn(Optional.of(item));
        when(repo.findBySellerAndTitleNormalized(eq("S"), eq(Texts.normalizeTitle("New title"))))
                .thenReturn(Optional.empty());
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateItemRQ rq = new UpdateItemRQ(
                "New title",
                "New desc",
                new PriceRQ("ARS", new BigDecimal("25.50")),
                7
        );
        ItemRS rs = service.update("ID1", rq);

        InOrder inOrder = inOrder(repo);
        inOrder.verify(repo).findById("ID1");
        inOrder.verify(repo).findBySellerAndTitleNormalized("S", Texts.normalizeTitle("New title"));
        inOrder.verify(repo).save(item);
        inOrder.verifyNoMoreInteractions();

        assertEquals("New title", item.getTitle());
        assertEquals("New desc", item.getDescription());
        assertEquals(new BigDecimal("25.50"), item.getBasePrice().amount());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(7, item.getStock());

        assertNotNull(rs);
        assertEquals("ID1", rs.id());
        assertEquals("New title", rs.title());
    }


    @Test
    @DisplayName("update: NotFound si no existe el item")
    void update_notFound() {
        when(repo.findById("ID1")).thenReturn(Optional.empty());
        var rq = new UpdateItemRQ("T", "D", new PriceRQ("ARS",new BigDecimal("1.00")), 1);

        assertThrows(NotFoundException.class, () -> service.update("ID1", rq));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("delete: ok cuando repo.deleteById retorna true")
    void delete_ok() {
        when(repo.deleteById("ID1")).thenReturn(true);

        service.delete("ID1");

        verify(repo).deleteById("ID1");
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("delete: NotFound cuando repo.deleteById retorna false")
    void delete_notFound() {
        when(repo.deleteById("ID1")).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.delete("ID1"));
        verify(repo).deleteById("ID1");
        verifyNoMoreInteractions(repo);
    }

    // ---------------- rate ----------------

    @Test
    @DisplayName("rate: actualiza rating, guarda y retorna ItemRS")
    void rate_ok() {
        var item = new ItemBuilder()
                .id("ID1").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("10.00")))
                .stock(1).sellerId("S").build();

        when(repo.findById("ID1")).thenReturn(Optional.of(item));
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemRS rs = service.rate("ID1", 5);

        verify(repo).findById("ID1");
        verify(repo).save(item);
        verifyNoMoreInteractions(repo);

        assertEquals(1, item.getRating().count());
        assertEquals(5.0, item.getRating().average(), 0.0001);

        assertNotNull(rs);
        assertEquals("ID1", rs.id());
    }

    @Test
    @DisplayName("rate: NotFound si no existe el item")
    void rate_notFound() {
        when(repo.findById("ID1")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.rate("ID1", 5));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    // ---------------- applyDiscount ----------------

    @Test
    @DisplayName("applyDiscount: setea descuento y guarda (ventana activa)")
    void applyDiscount_ok() {
        var nowRef = Instant.parse("2025-10-06T12:00:00Z");
        when(clock.instant()).thenReturn(nowRef);

        var item = new ItemBuilder()
                .id("ID1").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(1).sellerId("S").build();

        when(repo.findById("ID1")).thenReturn(Optional.of(item));
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant starts = nowRef.minus(1, ChronoUnit.DAYS);
        Instant ends   = nowRef.plus(1, ChronoUnit.DAYS);
        var rq = new ApplyDiscountRQ("PERCENT", 10L, "10% OFF", starts, ends);

        ItemRS rs = service.applyDiscount("ID1", rq);

        verify(repo).findById("ID1");
        verify(repo).save(item);
        verify(clock).instant(); // se usa al mapear ItemRS
        verifyNoMoreInteractions(repo);

        var active = item.getActiveDiscount(nowRef);
        assertTrue(active.isPresent());
        var d = active.get();
        assertEquals(DiscountType.PERCENT, d.type());
        assertEquals(10L, d.value());
        assertEquals("10% OFF", d.label());
        assertEquals(starts, d.startsAt());
        assertEquals(ends, d.endsAt());

        assertNotNull(rs);
        assertTrue(rs.hasActiveDiscount(), "Debe tener descuento activo");
        assertNotNull(rs.discount());
        assertEquals("PERCENT", rs.discount().type());
        assertEquals(10L, rs.discount().value());
        assertTrue(rs.discount().active());
    }

    @Test
    @DisplayName("applyDiscount: NotFound si no existe el item")
    void applyDiscount_notFound() {
        when(repo.findById("ID1")).thenReturn(Optional.empty());
        var rq = new ApplyDiscountRQ("PERCENT", 10L, "x", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS));

        assertThrows(NotFoundException.class, () -> service.applyDiscount("ID1", rq));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("applyDiscount: tipo inválido -> ApiException ENUM_INVALID")
    void applyDiscount_invalidType_throwsApiException() {
        when(repo.findById("ID1")).thenReturn(Optional.of(new ItemBuilder()
                .id("ID1").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("1.00")))
                .stock(1).sellerId("S").build()));

        var rq = new ApplyDiscountRQ("WHATEVER", 10L, "x", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS));

        assertThrows(ApiException.class, () -> service.applyDiscount("ID1", rq));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("applyDiscount: inputs inválidos (p.ej. percent > 100) -> ApiException INVALID_DISCOUNT")
    void applyDiscount_invalidDiscountInputs_throwsApiException() {
        when(repo.findById("ID1")).thenReturn(Optional.of(new ItemBuilder()
                .id("ID1").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("1.00")))
                .stock(1).sellerId("S").build()));

        var rq = new ApplyDiscountRQ("PERCENT", 150L, "x", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS));

        assertThrows(ApiException.class, () -> service.applyDiscount("ID1", rq));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    // ---------------- clearDiscount ----------------

    @Test
    @DisplayName("clearDiscount: limpia descuento y guarda")
    void clearDiscount_ok() {
        var nowRef = Instant.parse("2025-10-06T00:00:00Z");
        var item = new ItemBuilder()
                .id("ID1").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(1).sellerId("S")
                .discount(new Discount(
                        DiscountType.AMOUNT, 5L, "Save 5",
                        nowRef.minus(2, ChronoUnit.DAYS),
                        nowRef.plus(2, ChronoUnit.DAYS)
                ))
                .build();

        when(repo.findById("ID1")).thenReturn(Optional.of(item));
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemRS rs = service.clearDiscount("ID1");

        verify(repo).findById("ID1");
        verify(repo).save(item);
        verifyNoMoreInteractions(repo);

        assertTrue(item.getActiveDiscount(nowRef).isEmpty());

        assertNotNull(rs);
        assertFalse(rs.hasActiveDiscount());
        assertNull(rs.discount());
    }

    @Test
    @DisplayName("clearDiscount: NotFound si no existe el item")
    void clearDiscount_notFound() {
        when(repo.findById("ID1")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.clearDiscount("ID1"));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }
}
