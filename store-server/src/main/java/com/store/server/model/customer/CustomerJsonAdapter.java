package com.store.server.model.customer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.math.BigDecimal;

/** Polymorphic (de)serialization of {@link Customer}, keyed on the {@code "type"} field. */
public class CustomerJsonAdapter implements JsonSerializer<Customer>, JsonDeserializer<Customer> {

    @Override
    public JsonElement serialize(Customer c, Type typeOfSrc, JsonSerializationContext ctx) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.getId());
        o.addProperty("fullName", c.getFullName());
        o.addProperty("phone", c.getPhone());
        o.addProperty("type", c.getType().name());
        o.addProperty("track", c.getTrackDescription());   // output only, ignored when reading
        o.addProperty("purchaseCount", c.getPurchaseCount());
        o.addProperty("totalSpent", c.getTotalSpent());
        return o;
    }

    @Override
    public Customer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
        JsonObject o = json.getAsJsonObject();
        if (!o.has("id") || !o.has("type")) {
            throw new JsonParseException("Customer requires id and type");
        }
        CustomerType type;
        try {
            type = CustomerType.valueOf(o.get("type").getAsString());
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unknown customer type: " + o.get("type"));
        }
        return CustomerFactory.create(type,
                o.get("id").getAsString(),
                stringOrNull(o, "fullName"),
                stringOrNull(o, "phone"),
                o.has("purchaseCount") ? o.get("purchaseCount").getAsInt() : 0,
                o.has("totalSpent") ? o.get("totalSpent").getAsBigDecimal() : BigDecimal.ZERO);
    }

    private static String stringOrNull(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
