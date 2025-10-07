package com.ignacioramirez.itemDetailService.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignacioramirez.itemDetailService.domain.Item;
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
        init();
    }

    private void init() {
        lock.writeLock().lock();
        try {
            Files.createDirectories(dataPath.getParent());
            if (!Files.exists(dataPath)) {
                persist(); // crea archivo vacío inicial
                return;
            }
            // Cargar lista desde disco
            List<Item> items = mapper.readValue(Files.newBufferedReader(dataPath), new TypeReference<>() {});
            byId.clear();
            bySku.clear();
            for (Item it : items) {
                byId.put(it.getId(), it);
                bySku.put(it.getSku(), it.getId());
            }
        } catch (Exception e) {
            tryRestoreBackupOrReset();
        } finally {
            lock.writeLock().unlock();
        }
    }
    private void tryRestoreBackupOrReset() {
        try {
            if (Files.exists(bakPath)) {
                System.out.println("Intentando restaurar desde backup: " + bakPath);
                List<Item> items = mapper.readValue(Files.newBufferedReader(bakPath), new TypeReference<>() {});
                System.out.println("Items restaurados: " + items.size());
                byId.clear();
                bySku.clear();
                for (Item it : items) {
                    byId.put(it.getId(), it);
                    bySku.put(it.getSku(), it.getId());
                }
                persist();
                return;
            }
            System.out.println("No existe archivo de backup");
        } catch (Exception e) {
            System.err.println("Error restaurando backup: " + e.getMessage());
            e.printStackTrace();
        }
        byId.clear();
        bySku.clear();
        persist();
    }

    private void persist() {
        try {
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

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist items.json", e);
        }
    }

    // ====================== CRUD ======================

    @Override
    public Optional<Item> findById(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(byId.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Item> findBySku(String sku) {
        lock.readLock().lock();
        try {
            String id = bySku.get(sku);
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Item> findAll(int page, int size) {
        if (page < 0 || size <= 0) return List.of();
        lock.readLock().lock();
        try {
            var items = new ArrayList<>(byId.values());
            items.sort(Comparator.comparing(Item::getSku)
                    .thenComparing(Item::getId));
            int from = Math.min(page * size, items.size());
            int to = Math.min(from + size, items.size());
            return items.subList(from, to);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Item save(Item item) {
        Objects.requireNonNull(item, "item");
        String id = item.getId();
        String sku = item.getSku();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Item id is required");
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("Item sku is required");

        lock.writeLock().lock();
        try {
            String existingIdForSku = bySku.get(sku);
            if (existingIdForSku != null && !existingIdForSku.equals(id)) {
                throw new IllegalStateException("SKU already exists: " + sku);
            }

            byId.put(id, item);
            bySku.put(sku, id);

            persist();
            return item;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean deleteById(String id) {
        lock.writeLock().lock();
        try {
            Item removed = byId.remove(id);
            if (removed != null) {
                bySku.remove(removed.getSku());
                persist();
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try {
            return byId.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
