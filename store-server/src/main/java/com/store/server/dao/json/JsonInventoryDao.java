package com.store.server.dao.json;

import com.google.gson.reflect.TypeToken;
import com.store.server.dao.InventoryDao;
import com.store.server.model.InventoryItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stores all branches' stock in one file: {@code { "<branchId>": [items...] }}. */
public class JsonInventoryDao implements InventoryDao {

    private final Path file;
    private final Map<String, List<InventoryItem>> stock;

    public JsonInventoryDao(Path file) {
        this.file = file;
        this.stock = JsonFileStore.read(file,
                new TypeToken<LinkedHashMap<String, List<InventoryItem>>>() { }.getType(),
                LinkedHashMap::new);
    }

    @Override
    public synchronized List<InventoryItem> findByBranch(String branchId) {
        return copy(stock.getOrDefault(branchId, List.of()));
    }

    @Override
    public synchronized void saveBranch(String branchId, List<InventoryItem> items) {
        stock.put(branchId, copy(items));
        JsonFileStore.write(file, stock);
    }

    private static List<InventoryItem> copy(List<InventoryItem> items) {
        List<InventoryItem> result = new ArrayList<>(items.size());
        items.forEach(i -> result.add(i.copy()));
        return result;
    }
}
