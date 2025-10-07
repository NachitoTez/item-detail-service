package com.ignacioramirez.itemDetailService.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignacioramirez.itemDetailService.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileItemRepositoryTest {

    @TempDir
    Path tempDir;

    private ObjectMapper mapper;
    private FileItemRepository repository;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        repository = new FileItemRepository(mapper, tempDir.toString());
    }

    // ==================== TESTS BÁSICOS ====================

    @Test
    @DisplayName("DEBUG: Ver contenido del JSON y backup")
    void debugJsonContent() throws IOException {
        repository.save(createTestItem("1", "SKU-001", "Product 1", 100.0));
        repository.save(createTestItem("2", "SKU-002", "Product 2", 200.0));

        Path itemsFile = tempDir.resolve("items.json");
        Path bakFile = tempDir.resolve("items.json.bak");

        System.out.println("=== items.json ===");
        System.out.println(Files.readString(itemsFile));

        System.out.println("\n=== items.json.bak ===");
        if (Files.exists(bakFile)) {
            System.out.println(Files.readString(bakFile));
        } else {
            System.out.println("No existe");
        }

        // Intentar cargar manualmente
        try {
            List<Item> items = mapper.readValue(
                    Files.newBufferedReader(itemsFile),
                    new TypeReference<>() {
                    }
            );
            System.out.println("\n=== Items cargados: " + items.size());
        } catch (Exception e) {
            System.out.println("\n=== ERROR al cargar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("Debe crear archivo items.json vacío en inicialización")
    void shouldCreateEmptyJsonFileOnInit() {
        Path itemsFile = tempDir.resolve("items.json");
        assertThat(itemsFile).exists();
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Debe guardar y recuperar item por ID")
    void shouldSaveAndFindById() {
        Item item = createTestItem("1", "SKU-001", "Product 1", 100.0);

        repository.save(item);

        Optional<Item> found = repository.findById("1");
        assertThat(found).isPresent();
        assertThat(found.get().getSku()).isEqualTo("SKU-001");
        assertThat(found.get().getTitle()).isEqualTo("Product 1");
    }

    @Test
    @DisplayName("Debe buscar item por SKU")
    void shouldFindBySku() {
        Item item = createTestItem("1", "SKU-001", "Product 1", 100.0);
        repository.save(item);

        Optional<Item> found = repository.findBySku("SKU-001");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("1");
    }

    @Test
    @DisplayName("Debe actualizar item existente")
    void shouldUpdateExistingItem() {
        Item original = createTestItem("1", "SKU-001", "Product 1", 100.0);
        repository.save(original);

        Item updated = createTestItem("1", "SKU-001", "Product 1 Updated", 150.0);
        repository.save(updated);

        Optional<Item> found = repository.findById("1");
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Product 1 Updated");
        assertThat(found.get().getBasePrice().amount()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("Debe eliminar item por ID")
    void shouldDeleteById() {
        Item item = createTestItem("1", "SKU-001", "Product 1", 100.0);
        repository.save(item);

        boolean deleted = repository.deleteById("1");

        assertThat(deleted).isTrue();
        assertThat(repository.findById("1")).isEmpty();
        assertThat(repository.findBySku("SKU-001")).isEmpty();
    }

    @Test
    @DisplayName("Debe retornar false al eliminar item inexistente")
    void shouldReturnFalseWhenDeletingNonExistent() {
        boolean deleted = repository.deleteById("999");
        assertThat(deleted).isFalse();
    }

    // ==================== VALIDACIONES ====================

    @Test
    @DisplayName("Debe lanzar excepción si item es null")
    void shouldThrowExceptionWhenItemIsNull() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Debe lanzar excepción si ID es null (repository)")
    void shouldThrowWhenIdIsNull_onRepository() {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(null);
        when(item.getSku()).thenReturn("SKU-001");
        when(item.getBasePrice()).thenReturn(new Price("USD", new BigDecimal("100.00")));
        when(item.getStock()).thenReturn(10);
        when(item.getSellerId()).thenReturn("seller-1");

        assertThatThrownBy(() -> repository.save(item))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item id is required"); // cuidá el texto exacto
    }

    @Test
    @DisplayName("Debe lanzar excepción si SKU es null (repository)")
    void shouldThrowWhenSkuIsNull_onRepository() {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn("1");
        when(item.getSku()).thenReturn(null);
        when(item.getBasePrice()).thenReturn(new Price("USD", new BigDecimal("100.00")));
        when(item.getStock()).thenReturn(10);
        when(item.getSellerId()).thenReturn("seller-1");

        assertThatThrownBy(() -> repository.save(item))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item sku is required");
    }


    @Test
    @DisplayName("Debe lanzar excepción si SKU ya existe en otro item")
    void shouldThrowExceptionWhenSkuAlreadyExists() {
        Item item1 = createTestItem("1", "SKU-001", "Product 1", 100.0);
        Item item2 = createTestItem("2", "SKU-001", "Product 2", 200.0);

        repository.save(item1);

        assertThatThrownBy(() -> repository.save(item2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SKU already exists");
    }

    @Test
    @DisplayName("Debe permitir actualizar item manteniendo el mismo SKU")
    void shouldAllowUpdateWithSameSku() {
        Item original = createTestItem("1", "SKU-001", "Product 1", 100.0);
        repository.save(original);

        Item updated = createTestItem("1", "SKU-001", "Updated", 150.0);

        assertThatCode(() -> repository.save(updated))
                .doesNotThrowAnyException();
    }

    // ==================== PAGINACIÓN ====================

    @Test
    @DisplayName("Debe paginar resultados correctamente")
    void shouldPaginateResults() {
        // Crear 10 items
        IntStream.range(0, 10)
                .forEach(i -> repository.save(
                        createTestItem(String.valueOf(i), "SKU-00" + i, "Product " + i, 100.0)
                ));

        List<Item> page0 = repository.findAll(0, 3);
        List<Item> page1 = repository.findAll(1, 3);
        List<Item> page2 = repository.findAll(2, 3);

        assertThat(page0).hasSize(3);
        assertThat(page1).hasSize(3);
        assertThat(page2).hasSize(3);

        // Verificar que son diferentes
        assertThat(page0.getFirst().getId()).isNotEqualTo(page1.getFirst().getId());
    }

    @Test
    @DisplayName("Debe retornar lista vacía para página fuera de rango")
    void shouldReturnEmptyListForOutOfRangePage() {
        repository.save(createTestItem("1", "SKU-001", "Product", 100.0));

        List<Item> page = repository.findAll(10, 5);

        assertThat(page).isEmpty();
    }

    @Test
    @DisplayName("Debe retornar lista vacía para parámetros inválidos")
    void shouldReturnEmptyListForInvalidParams() {
        assertThat(repository.findAll(-1, 5)).isEmpty();
        assertThat(repository.findAll(0, 0)).isEmpty();
        assertThat(repository.findAll(0, -1)).isEmpty();
    }

    // ==================== PERSISTENCIA ====================

    @Test
    @DisplayName("Debe persistir datos en disco")
    void shouldPersistDataToDisk() throws IOException {
        Item item = createTestItem("1", "SKU-001", "Product 1", 100.0);
        repository.save(item);

        Path itemsFile = tempDir.resolve("items.json");
        assertThat(itemsFile).exists();

        String content = Files.readString(itemsFile);
        assertThat(content).contains("SKU-001");
        assertThat(content).contains("Product 1");
    }

    @Test
    @DisplayName("Debe crear backup antes de guardar")
    void shouldCreateBackupBeforeSaving() {
        Item item1 = createTestItem("1", "SKU-001", "Product 1", 100.0);
        repository.save(item1);

        Item item2 = createTestItem("2", "SKU-002", "Product 2", 200.0);
        repository.save(item2);

        Path backupFile = tempDir.resolve("items.json.bak");
        assertThat(backupFile).exists();
    }

    @Test
    @DisplayName("Debe restaurar desde backup si archivo principal corrupto")
    void shouldRestoreFromBackupIfMainFileCorrupted() throws IOException {
        Item item1 = createTestItem("1", "SKU-001", "Product 1", 100.0);
        Item item2 = createTestItem("2", "SKU-002", "Product 2", 200.0);
        repository.save(item1);
        repository.save(item2);

        Path itemsFile = tempDir.resolve("items.json");


        // Corromper archivo principal
        Files.writeString(itemsFile, "{ invalid json !!!");

        // Crear nuevo repositorio (dispara init())
        FileItemRepository newRepo = new FileItemRepository(mapper, tempDir.toString());

        assertThat(newRepo.count()).isEqualTo(2);
        assertThat(newRepo.findById("1")).isPresent();
        assertThat(newRepo.findById("2")).isPresent();
    }

    @Test
    @DisplayName("Debe cargar datos existentes al inicializar")
    void shouldLoadExistingDataOnInit() {
        repository.save(createTestItem("1", "SKU-001", "Product 1", 100.0));
        repository.save(createTestItem("2", "SKU-002", "Product 2", 200.0));

        // Crear nuevo repositorio con mismo directorio
        FileItemRepository newRepo = new FileItemRepository(mapper, tempDir.toString());

        assertThat(newRepo.count()).isEqualTo(2);
        assertThat(newRepo.findById("1")).isPresent();
        assertThat(newRepo.findById("2")).isPresent();
    }

    // ==================== CONCURRENCIA ====================

    @Test
    @DisplayName("Debe permitir múltiples lecturas concurrentes")
    void shouldAllowConcurrentReads() throws Exception {
        // Preparar datos
        IntStream.range(0, 100)
                .forEach(i -> repository.save(
                        createTestItem(String.valueOf(i), "SKU-" + i, "Product " + i, 100.0)
                ));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        List<Future<Optional<Item>>> futures = new ArrayList<>();

        // 10 threads leyendo simultáneamente
        for (int i = 0; i < 10; i++) {
            final int id = i;
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await();
                return repository.findById(String.valueOf(id));
            }));
        }

        // Todas las lecturas deben completarse exitosamente
        for (Future<Optional<Item>> future : futures) {
            assertThat(future.get(5, TimeUnit.SECONDS)).isPresent();
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("Debe manejar escrituras concurrentes de forma thread-safe")
    void shouldHandleConcurrentWritesSafely() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        int itemCount = 100;

        // 10 threads escribiendo 10 items cada uno
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 10; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                latch.countDown();
                try {
                    latch.await();
                    for (int i = 0; i < 10; i++) {
                        int id = threadId * 10 + i;
                        repository.save(createTestItem(
                                String.valueOf(id),
                                "SKU-" + id,
                                "Product " + id,
                                100.0
                        ));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        // Esperar que terminen todos
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        // Verificar que se guardaron todos
        assertThat(repository.count()).isEqualTo(itemCount);

        executor.shutdown();
    }

    @Test
    @DisplayName("Debe mantener consistencia con lecturas y escrituras concurrentes")
    void shouldMaintainConsistencyWithMixedOperations() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        int operationCount = 100;
        CountDownLatch startLatch = new CountDownLatch(20);

        // Preparar algunos datos iniciales
        IntStream.range(0, 50)
                .forEach(i -> repository.save(
                        createTestItem(String.valueOf(i), "SKU-" + i, "Product " + i, 100.0)
                ));

        List<Future<?>> futures = new ArrayList<>();

        // 10 threads escribiendo
        for (int t = 0; t < 10; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.countDown();
                try {
                    startLatch.await();
                    for (int i = 0; i < operationCount / 10; i++) {
                        int id = 50 + threadId * 10 + i;
                        repository.save(createTestItem(
                                String.valueOf(id),
                                "SKU-" + id,
                                "Product " + id,
                                100.0
                        ));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        // 10 threads leyendo
        for (int t = 0; t < 10; t++) {
            futures.add(executor.submit(() -> {
                startLatch.countDown();
                try {
                    startLatch.await();
                    for (int i = 0; i < operationCount; i++) {
                        int id = ThreadLocalRandom.current().nextInt(150);
                        repository.findById(String.valueOf(id));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        // Esperar que terminen
        for (Future<?> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }

        // Verificar consistencia: debe haber 150 items (50 iniciales + 100 nuevos)
        assertThat(repository.count()).isEqualTo(150);

        executor.shutdown();
    }

    @Test
    @DisplayName("Debe prevenir race condition en validación de SKU duplicado")
    void shouldPreventRaceConditionOnDuplicateSku() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);

        String sameSku = "DUPLICATE-SKU";
        List<Future<?>> futures = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // 5 threads intentando guardar items con el mismo SKU pero diferentes IDs
        for (int i = 0; i < 5; i++) {
            final int id = i;
            futures.add(executor.submit(() -> {
                latch.countDown();
                try {
                    latch.await();
                    repository.save(createTestItem(
                            String.valueOf(id),
                            sameSku,
                            "Product",
                            100.0
                    ));
                } catch (IllegalStateException e) {
                    exceptions.add(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }

        // Solo uno debe haber tenido éxito, los demás deben tener excepción
        assertThat(exceptions).hasSizeGreaterThanOrEqualTo(4);
        assertThat(repository.findBySku(sameSku)).isPresent();

        executor.shutdown();
    }

    // ==================== HELPERS ====================

    private Item createTestItem(String id, String sku, String title, double priceAmount) {
        return new ItemBuilder()
                .id(id)
                .sku(sku)
                .title(title)
                .description("Test description for " + title)
                .price(new Price("USD", BigDecimal.valueOf(priceAmount)))
                .stock(10)
                .sellerId("seller-test")
                .condition(Condition.NEW)
                .freeShipping(false)
                .build();
    }
}