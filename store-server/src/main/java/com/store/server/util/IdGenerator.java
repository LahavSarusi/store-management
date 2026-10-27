package com.store.server.util;

import java.util.UUID;

/** Short, prefixed, practically-unique ids (e.g. {@code SALE-1f3a9c2e}). */
public final class IdGenerator {

    private IdGenerator() { }

    public static String next(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
