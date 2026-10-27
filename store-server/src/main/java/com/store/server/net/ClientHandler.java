package com.store.server.net;

import com.google.gson.JsonParseException;
import com.store.server.model.Employee;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.JsonCodec;
import com.store.server.protocol.Request;
import com.store.server.protocol.Response;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * One instance per TCP connection, running on its own pool thread.
 * Reads newline-delimited JSON requests, dispatches them and writes the responses. Other threads may push
 * events at any time through {@link #send}, which is serialized with a write lock.
 */
public final class ClientHandler implements Runnable, ClientConnection {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final SessionManager sessions;
    private final SessionLifecycle lifecycle;
    private final Runnable onClosed;
    private final BufferedWriter out;
    private final Object writeLock = new Object();
    /** Work to run after the current response is written (only touched by this handler's thread). */
    private final List<Runnable> afterResponse = new ArrayList<>();
    private volatile Employee employee;

    public ClientHandler(Socket socket, RequestDispatcher dispatcher, SessionManager sessions,
                         SessionLifecycle lifecycle, Runnable onClosed) throws IOException {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.sessions = sessions;
        this.lifecycle = lifecycle;
        this.onClosed = onClosed;
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8));
    }

    @Override
    public void run() {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        LOG.info(() -> "Client connected: " + remote);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                send(handleLine(line));
                runAfterResponse();
            }
        } catch (IOException e) {
            LOG.fine(() -> "Connection " + remote + " lost: " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected error on " + remote, e);
        } finally {
            endSession("connection closed");
            close();
            onClosed.run();
            LOG.info(() -> "Client disconnected: " + remote);
        }
    }

    private Response handleLine(String line) {
        Request request;
        try {
            request = JsonCodec.decodeRequest(line);
        } catch (JsonParseException e) {
            return Response.error(null, ErrorCode.BAD_REQUEST, "Malformed JSON request");
        }
        return dispatcher.dispatch(request, this);
    }

    /** Thread-safe: used by this handler for responses and by other threads for events. */
    @Override
    public void send(Object message) {
        String json = JsonCodec.encode(message);
        synchronized (writeLock) {
            try {
                out.write(json);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                close();   // reader thread will notice and clean up
            }
        }
    }

    /**
     * Binds this connection to {@code e}. Fails if the user is already logged in elsewhere.
     * Presence (chat availability, pending-request notifications) starts after the LOGIN response is sent,
     * so the client always sees its login result first.
     */
    public boolean login(Employee e) {
        if (employee != null || !sessions.register(e.getUsername(), this)) {
            return false;
        }
        employee = e;
        afterResponse.add(() -> lifecycle.onLogin(e));
        return true;
    }

    /** Ends the session but keeps the connection open (another user may log in). */
    public void logout() {
        endSession("logout");
    }

    private void endSession(String reason) {
        Employee e = employee;
        if (e == null) {
            return;
        }
        employee = null;
        sessions.unregister(e.getUsername(), this);
        lifecycle.onLogout(e, reason);
    }

    private void runAfterResponse() {
        List<Runnable> tasks = List.copyOf(afterResponse);
        afterResponse.clear();
        tasks.forEach(Runnable::run);
    }

    @Override
    public Optional<Employee> employee() {
        return Optional.ofNullable(employee);
    }

    /** For handlers of routes the dispatcher already guarded with "must be logged in". */
    public Employee requireEmployee() {
        Employee e = employee;
        if (e == null) {
            throw new IllegalStateException("Route reached without login");
        }
        return e;
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
