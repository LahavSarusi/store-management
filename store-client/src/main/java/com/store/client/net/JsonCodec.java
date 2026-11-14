package com.store.client.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.lang.reflect.Type;

/**
 * The client's single Gson configuration. Null fields are omitted when encoding, which is what the server
 * expects for "unchanged" fields in update requests. Encoded output never contains raw newlines.
 */
public final class JsonCodec {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private JsonCodec() { }

    public static Gson gson() {
        return GSON;
    }

    public static String encode(Object value) {
        return GSON.toJson(value);
    }

    /** Parses one protocol line. */
    public static JsonObject parseLine(String line) {
        JsonElement tree = JsonParser.parseString(line);
        if (!tree.isJsonObject()) {
            throw new JsonParseException("Expected a JSON object");
        }
        return tree.getAsJsonObject();
    }

    /** Converts a payload tree into a DTO; a missing payload gives {@code null}. */
    public static <T> T decode(JsonElement payload, Type type) {
        if (payload == null || payload instanceof JsonNull) {
            return null;
        }
        return GSON.fromJson(payload, type);
    }

    public static JsonElement toTree(Object value) {
        return value == null ? JsonNull.INSTANCE : GSON.toJsonTree(value);
    }
}
