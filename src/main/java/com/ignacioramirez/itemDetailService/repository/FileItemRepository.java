package com.ignacioramirez.itemDetailService.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignacioramirez.itemDetailService.domain.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

//TODO revisar concurrencia
@Repository
public class FileItemRepository implements ItemRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileItemRepository.class);

    private final ObjectMapper mapper;
    private final Path dataPath;
    private final Path tmpPath;
    private final Path bakPath;

    // índices en memoria
    private final Map<String, Item> byId = new HashMap<>();
    private final Map<String, String> bySku = new HashMap<>();

    // lock RW para concurrencia
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public FileItemRepository(
            ObjectMapper mapper,
            @Value("${items.data-dir:data}") String dataDir
    ) {
        this.mapper = mapper;
        this.dataPath = Path.of(dataDir, "items.json");
        this.tmpPath  = Path.of(dataDir, "items.json.tmp");
        this.bakPath  = Path.of(dataDir, "items.json.bak");
        LOGGER.info("Initializing FileItemRepository with dataPath='{}'", dataPath);
        init();
    }

    private void init() {
        lock.writeLock().lock();
        try {
            LOGGER.info("Starting repository initialization");
            Files.createDirectories(dataPath.getParent());

            if (!Files.exists(dataPath)) {
                LOGGER.info("Data file does not exist, creating initial empty file");
                persist(); // crea archivo vacío inicial
                return;
            }

            // Cargar lista desde disco
            LOGGER.info("Loading items from '{}'", dataPath);
            List<Item> items = mapper.readValue(Files.newBufferedReader(dataPath), new TypeReference<>() {});
            byId.clear();
            bySku.clear();
            for (Item it : items) {
                byId.put(it.getId(), it);
                bySku.put(it.getSku(), it.getId());
            }
            LOGGER.info("Repository initialized successfully with {} items", byId.size());
        } catch (Exception e) {
            LOGGER.error("Error during initialization, attempting recovery", e);
            tryRestoreBackupOrReset();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void tryRestoreBackupOrReset() {
        try {
            if (Files.exists(bakPath)) {
                LOGGER.warn("Attempting to restore from backup: '{}'", bakPath);
                List<Item> items = mapper.readValue(Files.newBufferedReader(bakPath), new TypeReference<>() {});
                LOGGER.info("Restored {} items from backup", items.size());
                byId.clear();
                bySku.clear();
                for (Item it : items) {
                    byId.put(it.getId(), it);
                    bySku.put(it.getSku(), it.getId());
                }
                persist();
                return;
            }
            LOGGER.warn("No backup file exists at '{}'", bakPath);
        } catch (Exception e) {
            LOGGER.error("Error restoring backup", e);
        }
        LOGGER.warn("Resetting repository to empty state");
        byId.clear();
        bySku.clear();
        persist();
    }

    private void persist() {
        try {
            LOGGER.debug("Persisting {} items to disk", byId.size());
            Files.createDirectories(dataPath.getParent());

            // 1. Escribir a archivo temporal
            try (var w = Files.newBufferedWriter(tmpPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(w, new ArrayList<>(byId.values()));
            }

            // 2. Copiar el temporal al backup (el contenido NUEVO)
            Files.copy(tmpPath, bakPath, StandardCopyOption.REPLACE_EXISTING);

            // 3. Mover temporal a principal (atómico)
            Files.move(tmpPath, dataPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);

            LOGGER.debug("Persist completed successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to persist items to '{}'", dataPath, e);
            throw new UncheckedIOException("Failed to persist items.json", e);
        }
    }

    // ====================== CRUD ======================

    @Override
    public Optional<Item> findById(String id) {
        LOGGER.debug("Finding item by id='{}'", id);
        lock.readLock().lock();
        try {
            Optional<Item> result = Optional.ofNullable(byId.get(id));
            LOGGER.debug("Item with id='{}' {}", id, result.isPresent() ? "found" : "not found");
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Item> findBySku(String sku) {
        LOGGER.debug("Finding item by sku='{}'", sku);
        lock.readLock().lock();
        try {
            String id = bySku.get(sku);
            Optional<Item> result = id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
            LOGGER.debug("Item with sku='{}' {}", sku, result.isPresent() ? "found" : "not found");
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Item> findAll(int page, int size) {
        LOGGER.debug("Finding all items with page={}, size={}", page, size);
        if (page < 0 || size <= 0) {
            LOGGER.debug("Invalid pagination parameters, returning empty list");
            return List.of();
        }

        lock.readLock().lock();
        try {
            var items = new ArrayList<>(byId.values());
            items.sort(Comparator.comparing(Item::getSku)
                    .thenComparing(Item::getId));
            int from = Math.min(page * size, items.size());
            int to = Math.min(from + size, items.size());
            List<Item> result = items.subList(from, to);
            LOGGER.debug("Returning {} items for page={}", result.size(), page);
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Item save(Item item) {
        Objects.requireNonNull(item, "item");
        String id = item.getId();
        String sku = item.getSku();

        LOGGER.info("Saving item with id='{}', sku='{}'", id, sku);

        if (id == null || id.isBlank()) {
            LOGGER.error("Attempted to save item with null or blank id");
            throw new IllegalArgumentException("Item id is required");
        }
        if (sku == null || sku.isBlank()) {
            LOGGER.error("Attempted to save item with null or blank sku");
            throw new IllegalArgumentException("Item sku is required");
        }

        lock.writeLock().lock();
        try {
            String existingIdForSku = bySku.get(sku);
            if (existingIdForSku != null && !existingIdForSku.equals(id)) {
                LOGGER.error("SKU '{}' already exists for a different item (id='{}')", sku, existingIdForSku);
                throw new IllegalStateException("SKU already exists: " + sku);
            }

            byId.put(id, item);
            bySku.put(sku, id);
            persist();
            LOGGER.info("Item saved successfully with id='{}'", id);
            return item;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean deleteById(String id) {
        LOGGER.info("Deleting item with id='{}'", id);
        lock.writeLock().lock();
        try {
            Item removed = byId.remove(id);
            if (removed != null) {
                bySku.remove(removed.getSku());
                persist();
                LOGGER.info("Item with id='{}' deleted successfully", id);
                return true;
            }
            LOGGER.debug("Item with id='{}' not found, nothing to delete", id);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            long count = byId.size();
            LOGGER.debug("Current item count: {}", count);
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }
}