package com.ignacioramirez.itemDetailService.service;

import com.ignacioramirez.itemDetailService.domain.*;
import com.ignacioramirez.itemDetailService.dto.items.request.ApplyDiscountRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.PriceRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.UpdateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.exceptions.ConflictException;
import com.ignacioramirez.itemDetailService.exceptions.NotFoundException;
import com.ignacioramirez.itemDetailService.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    // ---------------- create ----------------

    @Test
    @DisplayName("create: conflicto si SKU ya existe")
    void create_conflictWhenSkuExists() {
        var rq = new CreateItemRQ(
                "SKU-1",
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

        when(repo.findBySku("SKU-1")).thenReturn(Optional.of(new ItemBuilder()
                .id("X").sku("SKU-1").title("old").description("old")
                .price(new Price("ARS", new BigDecimal("1.00")))
                .stock(1).sellerId("S").build()));

        assertThrows(ConflictException.class, () -> service.create(rq));
        verify(repo).findBySku("SKU-1");
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("create: guarda y retorna ItemRS")
    void create_ok() {
        var rq = new CreateItemRQ(
                "SKU-1",
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

        when(repo.findBySku("SKU-1")).thenReturn(Optional.empty());
        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.<Item>getArgument(0));

        ItemRS rs = service.create(rq);

        verify(repo).findBySku("SKU-1");
        verify(repo).save(captor.capture());
        verifyNoMoreInteractions(repo);

        Item saved = captor.getValue();
        assertEquals("SKU-1", saved.getSku());
        assertEquals("T", saved.getTitle());
        assertEquals("D", saved.getDescription());
        assertEquals("ARS", saved.getBasePrice().currency());
        assertEquals(new BigDecimal("10.00"), saved.getBasePrice().amount());
        assertEquals(1, saved.getStock());
        assertEquals("SELLER", saved.getSellerId());
        assertEquals(Condition.NEW, saved.getCondition());
        assertTrue(saved.isFreeShipping());

        assertNotNull(rs);
        assertEquals("SKU-1", rs.sku());
        assertEquals("T", rs.title());
    }

    // ---------------- getById ----------------

    @Test
    @DisplayName("getById: retorna ItemRS cuando existe")
    void getById_ok() {
        var item = new ItemBuilder()
                .id("ID1").sku("SKU-1").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("5.00")))
                .stock(1).sellerId("S").build();

        when(repo.findById("ID1")).thenReturn(Optional.of(item));

        ItemRS rs = service.getById("ID1");

        assertNotNull(rs);
        assertEquals("ID1", rs.id());
        assertEquals("SKU-1", rs.sku());
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
        var i1 = new ItemBuilder().id("I1").sku("S1").title("T1").description("D1")
                .price(new Price("ARS", new BigDecimal("1.00"))).stock(1).sellerId("S").build();
        var i2 = new ItemBuilder().id("I2").sku("S2").title("T2").description("D2")
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
                .id("ID1").sku("SKU-1").title("Old").description("Old")
                .price(new Price("ARS", new BigDecimal("10.00")))
                .stock(1).sellerId("S").build();

        when(repo.findById("ID1")).thenReturn(Optional.of(item));
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateItemRQ rq = new UpdateItemRQ(
                "New title",
                "New desc",
                new PriceRQ("ARS", new BigDecimal("25.50")),
                7
        );
        ItemRS rs = service.update("ID1", rq);

        verify(repo).findById("ID1");
        verify(repo).save(item);
        verifyNoMoreInteractions(repo);

        assertEquals("New title", item.getTitle());
        assertEquals("New desc", item.getDescription());
        assertEquals(new BigDecimal("25.50"), item.getBasePrice().amount());
        assertEquals("ARS", item.getBasePrice().currency());
        assertEquals(7, item.getStock());

        assertNotNull(rs);
        assertEquals("ID1", rs.id());
        assertEquals("SKU-1", rs.sku());
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
                .id("ID1").sku("SKU").title("T").description("D")
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
        // Tiempo de referencia fijo para el test
        var nowRef = Instant.parse("2025-10-06T12:00:00Z");
        // Configurar el clock mockeado para devolver nuestro tiempo fijo
        when(clock.instant()).thenReturn(nowRef);

        // Preparar item
        var item = new ItemBuilder()
                .id("ID1").sku("SKU").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("100.00")))
                .stock(1).sellerId("S").build();

        when(repo.findById("ID1")).thenReturn(Optional.of(item));
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        // Descuento activo: empieza ayer, termina mañana
        Instant starts = nowRef.minus(1, ChronoUnit.DAYS);
        Instant ends   = nowRef.plus(1, ChronoUnit.DAYS);
        var rq = new ApplyDiscountRQ("PERCENT", 10L, "10% OFF", starts, ends);

        // Ejecutar
        ItemRS rs = service.applyDiscount("ID1", rq);

        // Verificar interacciones
        verify(repo).findById("ID1");
        verify(repo).save(item);
        verify(clock).instant(); // Verificar que se usó el clock
        verifyNoMoreInteractions(repo);

        // Verificar estado del item
        var active = item.getActiveDiscount(nowRef);
        assertTrue(active.isPresent());
        var d = active.get();
        assertEquals(DiscountType.PERCENT, d.type());
        assertEquals(10L, d.value());
        assertEquals("10% OFF", d.label());
        assertEquals(starts, d.startsAt());
        assertEquals(ends, d.endsAt());

        // Verificar respuesta
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
    @DisplayName("applyDiscount: tipo inválido -> IllegalArgumentException (valueOf)")
    void applyDiscount_invalidType_throws() {
        when(repo.findById("ID1")).thenReturn(Optional.of(new ItemBuilder()
                .id("ID1").sku("SKU").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("1.00")))
                .stock(1).sellerId("S").build()));

        var rq = new ApplyDiscountRQ("WHATEVER", 10L, "x", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS));

        assertThrows(IllegalArgumentException.class, () -> service.applyDiscount("ID1", rq));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("applyDiscount: inputs inválidos del Discount (p.ej. percent > 100) -> IllegalArgumentException")
    void applyDiscount_invalidDiscountInputs_throws() {
        when(repo.findById("ID1")).thenReturn(Optional.of(new ItemBuilder()
                .id("ID1").sku("SKU").title("T").description("D")
                .price(new Price("ARS", new BigDecimal("1.00")))
                .stock(1).sellerId("S").build()));

        // percent 150 fuera de rango
        var rq = new ApplyDiscountRQ("PERCENT", 150L, "x", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS));

        assertThrows(IllegalArgumentException.class, () -> service.applyDiscount("ID1", rq));
        verify(repo).findById("ID1");
        verifyNoMoreInteractions(repo);
    }

    // ---------------- clearDiscount ----------------

    @Test
    @DisplayName("clearDiscount: limpia descuento y guarda")
    void clearDiscount_ok() {
        var nowRef = Instant.parse("2025-10-06T00:00:00Z");
        var item = new ItemBuilder()
                .id("ID1").sku("SKU").title("T").description("D")
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