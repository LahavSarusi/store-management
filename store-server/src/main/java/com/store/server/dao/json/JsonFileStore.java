package com.store.server.dao.json;

import com.google.gson.JsonParseException;
import com.store.server.protocol.JsonCodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** Reads and atomically writes JSON data files. */
final class JsonFileStore {

    private JsonFileStore() { }

    /** @return the parsed file, or {@code fallback} when the file does not exist or is empty */
    static <T> T read(Path file, Type type, Supplier<T> fallback) {
        if (!Files.exists(file)) {
            return fallback.get();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            T value = JsonCodec.gson().fromJson(json, type);
            return value == null ? fallback.get() : value;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (JsonParseException e) {
            throw new IllegalStateException("Corrupt data file " + file + ": " + e.getMessage(), e);
        }
    }

    /** Writes to a temp file then moves it over the target, so a crash never leaves a half-written file. */
    static void write(Path file, Object value) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
            Files.writeString(tmp, JsonCodec.prettyGson().toJson(value), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
