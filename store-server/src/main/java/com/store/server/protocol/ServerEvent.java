package com.store.server.protocol;

import java.time.Instant;

/**
 * Unsolicited push message.
 * <pre>{ "kind":"EVENT", "event":"INVENTORY_UPDATED", "payload":{...}, "timestamp":"2026-10-27T10:15:00Z" }</pre>
 */
public record ServerEvent(String kind, EventType event, Object payload, String timestamp) {

    public static ServerEvent of(EventType event, Object payload) {
        return new ServerEvent("EVENT", event, payload, Instant.now().toString());
    }
}
