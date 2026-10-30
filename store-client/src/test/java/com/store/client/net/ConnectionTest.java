package com.store.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.store.client.dto.Dtos.InventoryView;
import com.store.client.dto.Dtos.LoginRequest;
import com.store.client.dto.Dtos.LoginResult;
import com.store.client.dto.ErrorCode;
import com.store.client.dto.EventType;
import com.store.client.dto.RequestType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionTest {

    private FakeServer server;
    private Connection connection;

    @BeforeEach
    void connect() throws Exception {
        server = new FakeServer();
        connection = Connection.open("localhost", server.port());
    }

    @AfterEach
    void close() throws Exception {
        connection.close();
        server.close();
    }

    @Test
    void requestLineFollowsTheProtocol() throws Exception {
        connection.send(RequestType.LOGIN, new LoginRequest("tlv.cashier", "Store1234"));

        JsonObject r = server.nextRequest();
        assertEquals("REQUEST", r.get("kind").getAsString());
        assertEquals("LOGIN", r.get("type").getAsString());
        assertTrue(r.get("requestId").getAsString().startsWith("c-"));
        assertEquals("tlv.cashier", r.getAsJsonObject("payload").get("username").getAsString());
    }

    @Test
    void routeWithoutPayloadSendsNoPayloadField() throws Exception {
        connection.send(RequestType.BRANCH_LIST, null);
        assertFalse(server.nextRequest().has("payload"));
    }

    @Test
    void responsesAreMatchedByRequestIdEvenOutOfOrder() throws Exception {
        CompletableFuture<JsonElement> first = connection.send(RequestType.BRANCH_LIST, null);
        CompletableFuture<JsonElement> second = connection.send(RequestType.CUSTOMER_LIST, null);
        String firstId = server.nextRequest().get("requestId").getAsString();
        String secondId = server.nextRequest().get("requestId").getAsString();
        assertNotEquals(firstId, secondId);

        server.ok(secondId, "[\"second\"]");
        server.ok(firstId, "[\"first\"]");

        assertEquals("first", get(first).getAsJsonArray().get(0).getAsString());
        assertEquals("second", get(second).getAsJsonArray().get(0).getAsString());
    }

    @Test
    void okWithoutPayloadCompletesWithJsonNull() throws Exception {
        CompletableFuture<JsonElement> f = connection.send(RequestType.LOGOUT, null);
        server.ok(server.nextRequest().get("requestId").getAsString(), null);
        assertTrue(get(f).isJsonNull());
    }

    @Test
    void errorResponseFailsTheFutureWithItsCode() throws Exception {
        CompletableFuture<LoginResult> f = new ServerApi(connection).login("tlv.cashier", "Store1234");
        server.error(server.nextRequest().get("requestId").getAsString(), "ALREADY_LOGGED_IN", "User is already logged in");

        ServerException e = failure(f);
        assertEquals(ErrorCode.ALREADY_LOGGED_IN, e.code());
        assertEquals("User is already logged in", e.getMessage());
    }

    @Test
    void errorWithoutRequestIdFailsEveryPendingRequest() throws Exception {
        CompletableFuture<JsonElement> f = connection.send(RequestType.LOGIN, new LoginRequest("a", "b"));
        server.nextRequest();
        server.error(null, "SERVER_BUSY", "Server is full");

        assertEquals(ErrorCode.SERVER_BUSY, failure(f).code());
    }

    @Test
    void typedApiDecodesThePayload() throws Exception {
        CompletableFuture<InventoryView> f = new ServerApi(connection).inventory();
        server.ok(server.nextRequest().get("requestId").getAsString(),
                "{\"branchId\":\"TLV\",\"items\":[{\"sku\":\"SH-001\",\"name\":\"Shirt\",\"category\":\"SHIRTS\","
                        + "\"price\":79.9,\"quantity\":12}]}");

        InventoryView view = f.get(5, TimeUnit.SECONDS);
        assertEquals("TLV", view.branchId());
        assertEquals(12, view.items().get(0).quantity());
    }

    @Test
    void eventsReachListeners() throws Exception {
        List<ServerEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        connection.addEventListener(e -> {
            received.add(e);
            latch.countDown();
        });

        server.event("CUSTOMER_UPDATED", "{\"customer\":{\"id\":\"314159260\"}}");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(EventType.CUSTOMER_UPDATED, received.get(0).event());
        assertEquals("2026-10-30T10:00:00Z", received.get(0).timestamp());
    }

    @Test
    void unknownEventsAndMalformedLinesAreIgnored() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        connection.addEventListener(e -> latch.countDown());

        server.sendLine("not json");
        server.event("SOMETHING_NEW", "{}");
        CompletableFuture<JsonElement> f = connection.send(RequestType.BRANCH_LIST, null);
        server.ok(server.nextRequest().get("requestId").getAsString(), "[]");

        assertTrue(get(f).isJsonArray());
        assertFalse(latch.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void droppedConnectionFailsPendingRequestsAndNotifies() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        connection.addDisconnectListener(disconnected::countDown);
        CompletableFuture<JsonElement> f = connection.send(RequestType.BRANCH_LIST, null);
        server.nextRequest();

        server.dropClient();

        assertTrue(disconnected.await(5, TimeUnit.SECONDS));
        assertNull(failure(f).code());
        assertFalse(connection.isOpen());
        assertThrows(ExecutionException.class,
                () -> connection.send(RequestType.BRANCH_LIST, null).get(5, TimeUnit.SECONDS));
    }

    @Test
    void closingFromTheClientDoesNotReportADisconnect() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        connection.addDisconnectListener(disconnected::countDown);
        CompletableFuture<JsonElement> f = connection.send(RequestType.BRANCH_LIST, null);

        connection.close();

        assertNotNull(failure(f));
        assertFalse(disconnected.await(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void eventBusHoldsEventsUntilStarted() throws Exception {
        List<String> delivered = new CopyOnWriteArrayList<>();
        ClientEventBus bus = new ClientEventBus(connection, Runnable::run);
        bus.on(EventType.FORCED_LOGOUT, com.store.client.dto.Dtos.ForcedLogout.class, f -> delivered.add(f.reason()));

        server.event("FORCED_LOGOUT", "{\"reason\":\"bye\"}");
        // A response after the event proves the reader thread has already handled the event line.
        CompletableFuture<JsonElement> f = connection.send(RequestType.BRANCH_LIST, null);
        server.ok(server.nextRequest().get("requestId").getAsString(), "[]");
        get(f);
        assertTrue(delivered.isEmpty());

        bus.start();
        assertEquals(List.of("bye"), delivered);
    }

    private static JsonElement get(CompletableFuture<JsonElement> f) throws Exception {
        return f.get(5, TimeUnit.SECONDS);
    }

    private static ServerException failure(CompletableFuture<?> f) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> f.get(5, TimeUnit.SECONDS));
        return assertInstanceOf(ServerException.class, e.getCause());
    }
}
