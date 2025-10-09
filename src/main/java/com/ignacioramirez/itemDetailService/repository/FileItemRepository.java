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

@Repository
public class FileItemRepository implements ItemRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileItemRepository.class);

    private final ObjectMapper mapper;
    private final Path dataPath;
    private final Path tmpPath;
    private final Path bakPath;

    private final Map<String, Item> byId = new HashMap<>();
    private final Map<String, String> bySellerAndTitleNorm = new HashMap<>();

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
                persist();
                return;
            }

            LOGGER.info("Loading items from '{}'", dataPath);
            List<Item> items = mapper.readValue(Files.newBufferedReader(dataPath), new TypeReference<>() {});
            byId.clear();
            bySellerAndTitleNorm.clear();

            for (Item it : items) {
                byId.put(it.getId(), it);
                if (it.getSellerId() != null && !it.getSellerId().isBlank()
                        && it.getTitleNormalized() != null && !it.getTitleNormalized().isBlank()) {
                    String key = it.getSellerId() + "||" + it.getTitleNormalized();
                    bySellerAndTitleNorm.put(key, it.getId());
                }
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
                bySellerAndTitleNorm.clear();
                for (Item it : items) {
                    byId.put(it.getId(), it);
                    if (it.getSellerId() != null && !it.getSellerId().isBlank()
                            && it.getTitleNormalized() != null && !it.getTitleNormalized().isBlank()) {
                        String key = it.getSellerId() + "||" + it.getTitleNormalized();
                        bySellerAndTitleNorm.put(key, it.getId());
                    }
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
        bySellerAndTitleNorm.clear();
        persist();
    }

    private void persist() {
        try {
            LOGGER.debug("Persisting {} items to disk", byId.size());
            Files.createDirectories(dataPath.getParent());

            try (var w = Files.newBufferedWriter(tmpPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(w, new ArrayList<>(byId.values()));
            }

            Files.copy(tmpPath, bakPath, StandardCopyOption.REPLACE_EXISTING);

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
    public Optional<Item> findBySellerAndTitleNormalized(String sellerId, String titleNormalized) {
        LOGGER.debug("Finding by seller='{}' and titleNormalized='{}'", sellerId, titleNormalized);
        lock.readLock().lock();
        try {
            String id = bySellerAndTitleNorm.get(sellerId + "||" + titleNormalized);
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
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
            items.sort(
                    Comparator.comparing(
                            Item::getTitleNormalized,
                            Comparator.nullsLast(String::compareTo)
                    ).thenComparing(Item::getId)
            );

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

        LOGGER.info("Saving item with id='{}'", id);

        if (id == null || id.isBlank()) {
            LOGGER.error("Attempted to save item with null or blank id");
            throw new IllegalArgumentException("Item id is required");
        }
        if (item.getSellerId() == null || item.getSellerId().isBlank()) {
            LOGGER.error("Attempted to save item with null or blank sellerId");
            throw new IllegalArgumentException("Item sellerId is required");
        }
        if (item.getTitleNormalized() == null || item.getTitleNormalized().isBlank()) {
            LOGGER.error("Attempted to save item with null or blank titleNormalized");
            throw new IllegalArgumentException("Item titleNormalized is required");
        }

        String newKey = keyOf(item.getSellerId(), item.getTitleNormalized());

        lock.writeLock().lock();
        try {

            String existingId = bySellerAndTitleNorm.get(newKey);
            if (existingId != null && !existingId.equals(id)) {
                LOGGER.warn("Duplicate key for seller+title: '{}' (existingId='{}', newId='{}')",
                        newKey, existingId, id);
                throw new IllegalStateException("Another item already exists with the same sellerId and titleNormalized");
            }

            Item previous = byId.put(id, item);
            if (previous != null) {
                String oldKey = keyOf(previous.getSellerId(), previous.getTitleNormalized());
                if (!oldKey.equals(newKey)) {
                    bySellerAndTitleNorm.remove(oldKey, id);
                }
            }

            bySellerAndTitleNorm.put(newKey, id);

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
                String key = removed.getSellerId() + "||" + removed.getTitleNormalized();
                bySellerAndTitleNorm.remove(key, id);
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

    private String keyOf(String sellerId, String titleNormalized) {
        return sellerId + "||" + titleNormalized;
    }
}
