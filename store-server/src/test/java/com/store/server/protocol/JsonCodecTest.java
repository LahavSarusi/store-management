package com.store.server.protocol;

import com.google.gson.JsonParseException;
import com.store.server.model.customer.Customer;
import com.store.server.model.customer.CustomerFactory;
import com.store.server.model.customer.CustomerType;
import com.store.server.model.customer.VipCustomer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonCodecTest {

    @Test
    void customerRoundTripKeepsSubclassAndStatistics() {
        Customer vip = CustomerFactory.create(CustomerType.VIP, "161803390", "Dana", "054-7654321",
                4, new BigDecimal("999.90"));
        String json = JsonCodec.encode(vip);
        assertTrue(json.contains("\"type\":\"VIP\""));
        assertTrue(json.contains("\"track\":\"Cheapest item free"), json);

        Customer back = JsonCodec.gson().fromJson(json, Customer.class);
        assertInstanceOf(VipCustomer.class, back);
        assertEquals("Dana", back.getFullName());
        assertEquals(4, back.getPurchaseCount());
        assertEquals(0, new BigDecimal("999.90").compareTo(back.getTotalSpent()));
    }

    @Test
    void customerInsideEventPayloadIsSerializedPolymorphically() {
        Customer c = CustomerFactory.create(CustomerType.RETURNING, "1", "A", null);
        String json = JsonCodec.encode(ServerEvent.of(EventType.CUSTOMER_UPDATED, Map.of("customer", c)));
        assertTrue(json.contains("\"type\":\"RETURNING\""), json);
        assertTrue(json.contains("\"kind\":\"EVENT\""));
    }

    @Test
    void decodesRequestAndToleratesUnknownType() {
        Request r = JsonCodec.decodeRequest(
                "{\"kind\":\"REQUEST\",\"requestId\":\"7\",\"type\":\"LOGIN\",\"payload\":{\"username\":\"u\"}}");
        assertEquals(RequestType.LOGIN, r.type());
        assertEquals("u", r.payload().get("username").getAsString());

        Request unknown = JsonCodec.decodeRequest("{\"requestId\":\"8\",\"type\":\"HACK_THE_PLANET\"}");
        assertNull(unknown.type());
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(JsonParseException.class, () -> JsonCodec.decodeRequest("{not json"));
        assertThrows(JsonParseException.class, () -> JsonCodec.decodeRequest("[1,2]"));
        assertThrows(JsonParseException.class, () -> JsonCodec.decodeRequest("{\"type\":\"LOGIN\",\"payload\":[1]}"));
    }

    @Test
    void explicitNullPayloadMeansNoPayload() {
        Request r = JsonCodec.decodeRequest("{\"requestId\":\"1\",\"type\":\"INVENTORY_LIST\",\"payload\":null}");
        assertEquals(RequestType.INVENTORY_LIST, r.type());
        assertNull(r.payload());
    }

    @Test
    void encodedMessagesAreSingleLine() {
        String json = JsonCodec.encode(Response.ok("1", Map.of("text", "line1\nline2")));
        assertFalse(json.contains("\n"));
    }

    @Test
    void missingPayloadIsBadRequest() {
        DomainException e = assertThrows(DomainException.class, () -> JsonCodec.fromPayload(null, Map.class));
        assertEquals(ErrorCode.BAD_REQUEST, e.code());
    }
}
