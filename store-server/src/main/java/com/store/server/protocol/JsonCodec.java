package com.store.server.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.store.server.model.customer.Customer;
import com.store.server.model.customer.CustomerJsonAdapter;

/**
 * Single place that owns the Gson configuration, used both for the wire protocol and for file persistence.
 * Framing on the socket is one JSON object per line, so encoded output never contains raw newlines.
 */
public final class JsonCodec {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(Customer.class, new CustomerJsonAdapter())
            .disableHtmlEscaping()
            .create();

    private static final Gson PRETTY = GSON.newBuilder().setPrettyPrinting().create();

    private JsonCodec() { }

    public static Gson gson() {
        return GSON;
    }

    /** Pretty printer for human-readable data files. */
    public static Gson prettyGson() {
        return PRETTY;
    }

    /** Encodes a message as a single line of JSON. */
    public static String encode(Object message) {
        return GSON.toJson(message);
    }

    /**
     * Parses one request line. An explicit {@code "payload": null} is treated as no payload.
     * @throws JsonParseException when the line is not a JSON object or the payload is not an object
     */
    public static Request decodeRequest(String line) {
        JsonElement tree = JsonParser.parseString(line);
        if (!tree.isJsonObject()) {
            throw new JsonParseException("Request must be a JSON object");
        }
        JsonObject obj = tree.getAsJsonObject();
        JsonElement payload = obj.get("payload");
        if (payload != null && payload.isJsonNull()) {
            obj.remove("payload");
        } else if (payload != null && !payload.isJsonObject()) {
            throw new JsonParseException("payload must be a JSON object");
        }
        return GSON.fromJson(obj, Request.class);
    }

    /**
     * Converts a request payload into a DTO.
     * @throws DomainException BAD_REQUEST when the payload is missing or has the wrong shape
     */
    public static <T> T fromPayload(JsonObject payload, Class<T> type) {
        if (payload == null) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Missing payload");
        }
        try {
            return GSON.fromJson(payload, type);
        } catch (JsonParseException e) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Invalid payload: " + e.getMessage());
        }
    }

    /** Converts any object into a JSON tree (handy for building requests in tests and clients). */
    public static JsonObject toJsonObject(Object value) {
        JsonElement tree = GSON.toJsonTree(value);
        return tree.getAsJsonObject();
    }
}
