package com.store.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.store.client.dto.ErrorCode;
import com.store.client.dto.EventType;
import com.store.client.dto.RequestType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One TCP connection to the server, which is also one login session.
 * <p>
 * Writes are synchronized; a single daemon reader thread reads one JSON object per line and either completes
 * the future waiting for that {@code requestId} (RESPONSE) or hands the line to the event listeners (EVENT).
 * Listeners run on the reader thread, so UI code must go through {@link ClientEventBus}.
 */
public final class Connection implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Connection.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final Map<String, CompletableFuture<JsonElement>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final List<Consumer<ServerEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> disconnectListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean closedByClient;

    private Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(this::readLoop, "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public static Connection open(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return new Connection(socket);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /** Called on the reader thread for every pushed event. */
    public void addEventListener(Consumer<ServerEvent> listener) {
        eventListeners.add(listener);
    }

    /** Called once, on the reader thread, when the server closes the connection or it drops. Not after {@link #close()}. */
    public void addDisconnectListener(Runnable listener) {
        disconnectListeners.add(listener);
    }

    public boolean isOpen() {
        return !closed.get();
    }

    /**
     * Sends a request. The future completes with the response payload ({@link JsonNull} when there is none),
     * or fails with {@link ServerException}.
     */
    public CompletableFuture<JsonElement> send(RequestType type, Object payload) {
        String requestId = "c-" + nextId.getAndIncrement();
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        if (closed.get()) {
            future.completeExceptionally(ServerException.connectionLost());
            return future;
        }
        pending.put(requestId, future);

        JsonObject request = new JsonObject();
        request.addProperty("kind", "REQUEST");
        request.addProperty("requestId", requestId);
        request.addProperty("type", type.name());
        if (payload != null) {
            request.add("payload", JsonCodec.toTree(payload));
        }
        try {
            synchronized (out) {
                out.write(JsonCodec.encode(request));
                out.write('\n');
                out.flush();
            }
        } catch (IOException e) {
            pending.remove(requestId);
            future.completeExceptionally(ServerException.connectionLost());
        }
        if (closed.get()) {
            // Closed while we were sending; shutdown may already have drained the map.
            failPending(ServerException.connectionLost());
        }
        return future;
    }

    @Override
    public void close() {
        closedByClient = true;
        shutdown(ServerException.connectionLost());
    }

    // ------------------------------------------------------------------ reader thread

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    handleLine(line);
                }
            }
        } catch (IOException e) {
            if (!closedByClient) {
                LOG.log(Level.FINE, "Connection read failed", e);
            }
        } finally {
            boolean notify = !closedByClient && !closed.get();
            shutdown(ServerException.connectionLost());
            if (notify) {
                disconnectListeners.forEach(Connection::runSafely);
            }
        }
    }

    private void handleLine(String line) {
        JsonObject msg;
        try {
            msg = JsonCodec.parseLine(line);
        } catch (JsonParseException e) {
            LOG.warning("Ignoring malformed line from server: " + line);
            return;
        }
        String kind = stringOrNull(msg, "kind");
        if ("RESPONSE".equals(kind)) {
            handleResponse(msg);
        } else if ("EVENT".equals(kind)) {
            handleEvent(msg);
        } else {
            LOG.warning("Ignoring line of unknown kind: " + line);
        }
    }

    private void handleResponse(JsonObject msg) {
        String requestId = stringOrNull(msg, "requestId");
        boolean ok = "OK".equals(stringOrNull(msg, "status"));
        if (requestId == null) {
            // Not tied to a request, e.g. SERVER_BUSY sent right before the server closes the socket.
            if (!ok) {
                failPending(errorOf(msg));
            }
            return;
        }
        CompletableFuture<JsonElement> future = pending.remove(requestId);
        if (future == null) {
            LOG.warning("Response for unknown request " + requestId);
            return;
        }
        if (ok) {
            JsonElement payload = msg.get("payload");
            future.complete(payload == null ? JsonNull.INSTANCE : payload);
        } else {
            future.completeExceptionally(errorOf(msg));
        }
    }

    private void handleEvent(JsonObject msg) {
        EventType type;
        try {
            type = EventType.valueOf(stringOrNull(msg, "event"));
        } catch (IllegalArgumentException | NullPointerException e) {
            LOG.warning("Ignoring unknown event: " + msg.get("event"));
            return;
        }
        ServerEvent event = new ServerEvent(type, msg.get("payload"), stringOrNull(msg, "timestamp"));
        for (Consumer<ServerEvent> listener : eventListeners) {
            runSafely(() -> listener.accept(event));
        }
    }

    // ------------------------------------------------------------------ internals

    private void shutdown(ServerException cause) {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
        failPending(cause);
    }

    private void failPending(ServerException cause) {
        for (String id : List.copyOf(pending.keySet())) {
            CompletableFuture<JsonElement> f = pending.remove(id);
            if (f != null) {
                f.completeExceptionally(cause);
            }
        }
    }

    private static ServerException errorOf(JsonObject msg) {
        JsonObject error = msg.has("error") && msg.get("error").isJsonObject() ? msg.getAsJsonObject("error") : null;
        if (error == null) {
            return new ServerException(null, "The server reported an error.");
        }
        ErrorCode code = null;
        String codeName = stringOrNull(error, "code");
        if (codeName != null) {
            try {
                code = ErrorCode.valueOf(codeName);
            } catch (IllegalArgumentException ignored) {
                // newer server; keep the message
            }
        }
        String message = stringOrNull(error, "message");
        return new ServerException(code, message != null ? message : String.valueOf(codeName));
    }

    private static String stringOrNull(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static void runSafely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Listener failed", e);
        }
    }
}
