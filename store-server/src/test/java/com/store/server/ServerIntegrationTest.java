package com.store.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.store.server.net.StoreServer;
import com.store.server.protocol.EventType;
import com.store.server.protocol.JsonCodec;
import com.store.server.protocol.Request;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.dto.Dtos.ChatRequestIdRequest;
import com.store.server.protocol.dto.Dtos.ChatTargetRequest;
import com.store.server.protocol.dto.Dtos.LoginRequest;
import com.store.server.protocol.dto.Dtos.RestockRequest;
import com.store.server.protocol.dto.Dtos.SaleLineRequest;
import com.store.server.protocol.dto.Dtos.SaleRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real server on a random port and talks to it over TCP, like the client application will. */
class ServerIntegrationTest {

    @TempDir
    Path dir;

    private ServerContext context;
    private StoreServer server;
    private int port;
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        context = new ServerContext(dir);
        context.seedIfEmpty();
        server = context.createServer(0, 20);
        port = server.open();
        Thread t = new Thread(server::serve, "test-server");
        t.setDaemon(true);
        t.start();
    }

    @AfterEach
    void stop() {
        clients.forEach(TestClient::close);
        server.close();
        context.close();
    }

    private TestClient connect() throws IOException {
        TestClient c = new TestClient(port);
        clients.add(c);
        return c;
    }

    private TestClient loggedIn(String username) throws Exception {
        TestClient c = connect();
        JsonObject r = c.call(RequestType.LOGIN, new LoginRequest(username, DataSeeder.STAFF_PASSWORD));
        assertEquals("OK", r.get("status").getAsString(), r.toString());
        return c;
    }

    @Test
    void preventsDoubleLoginFromAnotherComputer() throws Exception {
        TestClient first = loggedIn("tlv.cashier");

        TestClient second = connect();
        JsonObject rejected = second.call(RequestType.LOGIN, new LoginRequest("tlv.cashier", DataSeeder.STAFF_PASSWORD));
        assertEquals("ALREADY_LOGGED_IN", errorCode(rejected));

        first.call(RequestType.LOGOUT, null);
        JsonObject retry = second.call(RequestType.LOGIN, new LoginRequest("tlv.cashier", DataSeeder.STAFF_PASSWORD));
        assertEquals("OK", retry.get("status").getAsString());
    }

    @Test
    void droppedConnectionReleasesTheLogin() throws Exception {
        TestClient first = loggedIn("tlv.cashier");
        first.close();
        // the server notices the closed socket asynchronously
        long deadline = System.currentTimeMillis() + 5000;
        while (context.sessions().isOnline("tlv.cashier") && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        loggedIn("tlv.cashier");
    }

    @Test
    void rejectsBadCredentialsUnauthenticatedAndForbiddenCalls() throws Exception {
        TestClient c = connect();
        assertEquals("UNAUTHENTICATED", errorCode(c.call(RequestType.INVENTORY_LIST, null)));
        assertEquals("INVALID_CREDENTIALS",
                errorCode(c.call(RequestType.LOGIN, new LoginRequest("tlv.cashier", "nope"))));

        c.call(RequestType.LOGIN, new LoginRequest("tlv.cashier", DataSeeder.STAFF_PASSWORD));
        assertEquals("FORBIDDEN", errorCode(c.call(RequestType.INVENTORY_RESTOCK,
                new RestockRequest("SH-001", 5, null, null, null))));
        assertEquals("BAD_REQUEST", errorCode(c.rawCall("{\"requestId\":\"x\",\"type\":\"NO_SUCH_ROUTE\"}", "x")));
    }

    @Test
    void saleSyncsInventoryToSameBranchAndCustomersToEveryone() throws Exception {
        TestClient cashier = loggedIn("tlv.cashier");
        TestClient sameBranch = loggedIn("tlv.manager");
        TestClient otherBranch = loggedIn("hfa.manager");

        JsonObject r = cashier.call(RequestType.SALE_CREATE,
                new SaleRequest("314159260", List.of(new SaleLineRequest("SH-001", 2))));
        assertEquals("OK", r.get("status").getAsString(), r.toString());

        JsonObject inv = sameBranch.awaitEvent(EventType.INVENTORY_UPDATED);
        assertEquals("TLV", inv.getAsJsonObject("payload").get("branchId").getAsString());
        assertNotNull(otherBranch.awaitEvent(EventType.CUSTOMER_UPDATED));
        assertFalse(otherBranch.receivedEvent(EventType.INVENTORY_UPDATED, 300));
    }

    @Test
    void queuedChatRequestIsDeliveredWhenTargetLogsIn() throws Exception {
        TestClient tlv = loggedIn("tlv.cashier");
        JsonObject queued = tlv.call(RequestType.CHAT_REQUEST, new ChatTargetRequest("HFA"));
        assertEquals("QUEUED", queued.getAsJsonObject("payload").get("status").getAsString());

        TestClient hfa = loggedIn("hfa.sales");
        JsonObject offer = hfa.awaitEvent(EventType.CHAT_PENDING_REQUEST);
        String requestId = offer.getAsJsonObject("payload").get("requestId").getAsString();

        JsonObject accepted = hfa.call(RequestType.CHAT_ACCEPT, new ChatRequestIdRequest(requestId));
        assertEquals("OK", accepted.get("status").getAsString(), accepted.toString());
        assertNotNull(tlv.awaitEvent(EventType.CHAT_STARTED));
    }

    private static String errorCode(JsonObject response) {
        return response.getAsJsonObject("error").get("code").getAsString();
    }

    /** Minimal protocol client: one reader thread splits responses from events. */
    private static final class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedWriter out;
        private final BlockingQueue<JsonObject> responses = new LinkedBlockingQueue<>();
        private final BlockingQueue<JsonObject> events = new LinkedBlockingQueue<>();
        /** Every event ever received, including ones awaitEvent skipped. */
        private final List<JsonObject> history = java.util.Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger seq = new AtomicInteger();

        TestClient(int port) throws IOException {
            socket = new Socket("localhost", port);
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
                        if ("EVENT".equals(msg.get("kind").getAsString())) {
                            history.add(msg);
                            events.add(msg);
                        } else {
                            responses.add(msg);
                        }
                    }
                } catch (IOException ignored) {
                    // closed
                }
            });
            reader.setDaemon(true);
            reader.start();
        }

        JsonObject call(RequestType type, Object payload) throws Exception {
            String id = "c-" + seq.incrementAndGet();
            JsonObject body = payload == null ? null : JsonCodec.toJsonObject(payload);
            return rawCall(JsonCodec.encode(Request.of(id, type, body)), id);
        }

        JsonObject rawCall(String json, String id) throws Exception {
            out.write(json);
            out.newLine();
            out.flush();
            while (true) {
                JsonObject r = responses.poll(5, TimeUnit.SECONDS);
                assertNotNull(r, "no response to " + id);
                if (r.has("requestId") && id.equals(r.get("requestId").getAsString())) {
                    return r;
                }
            }
        }

        JsonObject awaitEvent(EventType type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                JsonObject e = events.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (e != null && type.name().equals(e.get("event").getAsString())) {
                    return e;
                }
            }
            fail("event " + type + " not received");
            return null;
        }

        boolean receivedEvent(EventType type, long waitMillis) throws InterruptedException {
            Thread.sleep(waitMillis);
            synchronized (history) {
                return history.stream().anyMatch(e -> type.name().equals(e.get("event").getAsString()));
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }
}
