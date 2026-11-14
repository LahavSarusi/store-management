package com.store.client.net;

import com.google.gson.JsonElement;
import com.store.client.dto.EventType;

/** A pushed {@code {"kind":"EVENT", ...}} line; the payload is decoded by whoever handles the event. */
public record ServerEvent(EventType event, JsonElement payload, String timestamp) { }
