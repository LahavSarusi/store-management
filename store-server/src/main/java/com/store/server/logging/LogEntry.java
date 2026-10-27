package com.store.server.logging;

/** One parsed line of the action log. {@code timestamp} is {@code yyyy-MM-dd HH:mm:ss} local time. */
public record LogEntry(String timestamp, ActionType action, String actor, String details) { }
