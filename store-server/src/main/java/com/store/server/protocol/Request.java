package com.store.server.protocol;

import com.google.gson.JsonObject;

/**
 * Client-to-server message.
 * <pre>{ "kind":"REQUEST", "requestId":"c-17", "type":"SALE_CREATE", "payload":{...} }</pre>
 * {@code type} is null when the client sent an unknown route name.
 */
public record Request(String kind, String requestId, RequestType type, JsonObject payload) {

    public static Request of(String requestId, RequestType type, JsonObject payload) {
        return new Request("REQUEST", requestId, type, payload);
    }
}
