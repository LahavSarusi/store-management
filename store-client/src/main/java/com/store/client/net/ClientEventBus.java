package com.store.client.net;

import com.store.client.dto.EventType;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Delivers server events to UI handlers on the UI thread.
 * <p>
 * Events that arrive before {@link #start()} are held back and delivered, in order, when it is called. This lets
 * the main window register all its handlers first, so nothing pushed right after login (such as a waiting chat
 * request) is lost.
 */
public final class ClientEventBus {

    private final Executor uiExecutor;
    private final Map<EventType, List<Consumer<ServerEvent>>> handlers = new EnumMap<>(EventType.class);
    private final List<ServerEvent> held = new ArrayList<>();
    private boolean started;

    /** Delivers events on the Swing event dispatch thread. */
    public ClientEventBus(Connection connection) {
        this(connection, SwingUtilities::invokeLater);
    }

    public ClientEventBus(Connection connection, Executor uiExecutor) {
        this.uiExecutor = uiExecutor;
        connection.addEventListener(this::receive);
    }

    /** Registers a handler that gets the raw event. Call from the UI thread. */
    public synchronized void on(EventType type, Consumer<ServerEvent> handler) {
        handlers.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Registers a handler that gets the payload decoded as {@code payloadType}. Call from the UI thread. */
    public <T> void on(EventType type, Class<T> payloadType, Consumer<T> handler) {
        on(type, e -> handler.accept(JsonCodec.<T>decode(e.payload(), payloadType)));
    }

    /** Starts delivering events, beginning with the ones held back so far. */
    public void start() {
        List<ServerEvent> backlog;
        synchronized (this) {
            if (started) {
                return;
            }
            started = true;
            backlog = List.copyOf(held);
            held.clear();
        }
        backlog.forEach(e -> uiExecutor.execute(() -> dispatch(e)));
    }

    private void receive(ServerEvent event) {
        synchronized (this) {
            if (!started) {
                held.add(event);
                return;
            }
        }
        uiExecutor.execute(() -> dispatch(event));
    }

    private void dispatch(ServerEvent event) {
        List<Consumer<ServerEvent>> list;
        synchronized (this) {
            list = handlers.getOrDefault(event.event(), List.of());
        }
        list.forEach(h -> h.accept(event));
    }
}
