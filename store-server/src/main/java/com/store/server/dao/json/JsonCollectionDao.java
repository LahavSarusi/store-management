package com.store.server.dao.json;

import com.google.gson.reflect.TypeToken;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base for DAOs that keep a keyed collection in memory and persist it as a JSON array.
 * Every write rewrites the file; fine for the data volumes of a store network.
 * All access is synchronized on the DAO instance.
 */
abstract class JsonCollectionDao<T> {

    private final Path file;
    private final Function<T, String> keyOf;
    private final Map<String, T> items = new LinkedHashMap<>();

    protected JsonCollectionDao(Path file, Class<T> elementType, Function<T, String> keyOf) {
        this.file = file;
        this.keyOf = keyOf;
        List<T> loaded = JsonFileStore.read(file,
                TypeToken.getParameterized(List.class, elementType).getType(), List::of);
        loaded.forEach(item -> items.put(keyOf.apply(item), item));
    }

    protected synchronized Optional<T> get(String key) {
        return Optional.ofNullable(items.get(key));
    }

    protected synchronized List<T> values() {
        return new ArrayList<>(items.values());
    }

    protected synchronized void put(T item) {
        items.put(keyOf.apply(item), item);
        flush();
    }

    protected synchronized boolean remove(String key) {
        boolean removed = items.remove(key) != null;
        if (removed) {
            flush();
        }
        return removed;
    }

    private void flush() {
        JsonFileStore.write(file, new ArrayList<>(items.values()));
    }
}
