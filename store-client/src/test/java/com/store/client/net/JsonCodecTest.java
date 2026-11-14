package com.store.client.net;

import com.google.gson.JsonObject;
import com.store.client.dto.CustomerType;
import com.store.client.dto.Dtos.ChatPending;
import com.store.client.dto.Dtos.ChatRequestResult;
import com.store.client.dto.Dtos.Customer;
import com.store.client.dto.Dtos.CustomerEvent;
import com.store.client.dto.Dtos.CustomerRequest;
import com.store.client.dto.Dtos.EmployeeRequest;
import com.store.client.dto.Dtos.InventoryView;
import com.store.client.dto.Dtos.LoginResult;
import com.store.client.dto.Dtos.SaleQuote;
import com.store.client.dto.Permission;
import com.store.client.dto.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Decodes JSON in the exact shape the server produces (taken from its DTOs and CustomerJsonAdapter). */
class JsonCodecTest {

    @Test
    void decodesLoginResult() {
        String json = "{\"employee\":{\"username\":\"tlv.cashier\",\"employeeNumber\":1005,"
                + "\"nationalId\":\"314159260\",\"fullName\":\"Dana Levi\",\"phone\":\"0501234567\","
                + "\"bankAccount\":\"12-345-678901\",\"branchId\":\"TLV\",\"role\":\"CASHIER\"},"
                + "\"branch\":{\"id\":\"TLV\",\"name\":\"Tel Aviv - Dizengoff\",\"city\":\"Tel Aviv\"},"
                + "\"permissions\":[\"VIEW_CUSTOMERS\",\"SELL\",\"CHAT\"],\"passwordExpired\":true}";

        LoginResult r = decode(json, LoginResult.class);

        assertEquals(Role.CASHIER, r.employee().role());
        assertEquals(1005, r.employee().employeeNumber());
        assertEquals("Tel Aviv - Dizengoff", r.branch().name());
        assertEquals(Set.of(Permission.VIEW_CUSTOMERS, Permission.SELL, Permission.CHAT), r.permissions());
        assertTrue(r.passwordExpired());
    }

    @Test
    void adminLoginHasNoBranch() {
        LoginResult r = decode("{\"employee\":{\"username\":\"admin\",\"role\":\"ADMIN\"},"
                + "\"permissions\":[\"MANAGE_EMPLOYEES\"],\"passwordExpired\":false}", LoginResult.class);
        assertNull(r.branch());
        assertNull(r.employee().branchId());
    }

    @Test
    void decodesCustomerWithTypeAndTrack() {
        String json = "{\"customer\":{\"id\":\"314159260\",\"fullName\":\"Noa Cohen\",\"phone\":\"0521112233\","
                + "\"type\":\"VIP\",\"track\":\"15% off every purchase\",\"purchaseCount\":7,\"totalSpent\":2450.5}}";

        Customer c = decode(json, CustomerEvent.class).customer();

        assertEquals(CustomerType.VIP, c.type());
        assertEquals("15% off every purchase", c.track());
        assertEquals(7, c.purchaseCount());
        assertEquals(0, new BigDecimal("2450.50").compareTo(c.totalSpent()));
    }

    @Test
    void decodesInventoryWithExactPrices() {
        InventoryView v = decode("{\"branchId\":\"HFA\",\"items\":[{\"sku\":\"PA-002\",\"name\":\"Jeans\","
                + "\"category\":\"PANTS\",\"price\":199.90,\"quantity\":3}]}", InventoryView.class);
        assertEquals(new BigDecimal("199.90"), v.items().get(0).price());
    }

    @Test
    void decodesQueuedChatRequestAndPendingOffer() {
        ChatRequestResult r = decode("{\"status\":\"QUEUED\",\"requestId\":\"REQ-1\",\"position\":2}",
                ChatRequestResult.class);
        assertEquals(ChatRequestResult.Status.QUEUED, r.status());
        assertEquals(2, r.position());
        assertNull(r.sessionId());

        ChatPending p = decode("{\"requestId\":\"REQ-1\",\"from\":\"tlv.sales\",\"fromBranch\":\"TLV\","
                + "\"requestedAt\":1761818400000}", ChatPending.class);
        assertEquals("tlv.sales", p.from());
        assertEquals(1761818400000L, p.requestedAt());
    }

    @Test
    void decodesQuoteLines() {
        SaleQuote q = decode("{\"customerId\":\"314159260\",\"customerType\":\"RETURNING\",\"lines\":["
                + "{\"sku\":\"SH-001\",\"productName\":\"Shirt\",\"category\":\"SHIRTS\",\"unitPrice\":50,\"quantity\":3}],"
                + "\"result\":{\"subtotal\":150,\"discount\":7.5,\"total\":142.5,\"benefits\":[\"5% off\"]}}",
                SaleQuote.class);
        assertEquals(0, new BigDecimal("150").compareTo(q.lines().get(0).lineTotal()));
        assertEquals("5% off", q.result().benefits().get(0));
    }

    @Test
    void nullFieldsAreLeftOutSoTheServerKeepsThem() {
        JsonObject update = JsonCodec.toTree(
                new EmployeeRequest("tlv.cashier", null, null, "New Name", null, null, null, null)).getAsJsonObject();
        assertEquals(Set.of("username", "fullName"), update.keySet());

        JsonObject customer = JsonCodec.toTree(new CustomerRequest("314159260", null, null, CustomerType.VIP))
                .getAsJsonObject();
        assertEquals("VIP", customer.get("type").getAsString());
        assertFalse(customer.has("fullName"));
    }

    @Test
    void encodedRequestsStayOnOneLine() {
        String encoded = JsonCodec.encode(new CustomerRequest("1", "line one\nline two", null, null));
        assertFalse(encoded.contains("\n"));
    }

    private static <T> T decode(String json, Class<T> type) {
        return JsonCodec.decode(JsonCodec.parseLine(json), type);
    }
}
