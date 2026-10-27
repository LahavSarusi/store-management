package com.store.server;

import com.store.server.chat.ChatMetadata;
import com.store.server.chat.ChatRequest;
import com.store.server.dao.PendingChatDao;
import com.store.server.event.EventBus;
import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.logging.LogEntry;
import com.store.server.model.Employee;
import com.store.server.model.Role;
import com.store.server.protocol.EventType;
import com.store.server.protocol.ServerEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-written fakes shared by the unit tests. */
public final class TestSupport {

    private TestSupport() { }

    public static Employee employee(String username, String branchId, Role role) {
        return new Employee(username, "x", 0, null, username, null, null, branchId, role, System.currentTimeMillis());
    }

    /** Records every published event with its scope. */
    public static final class RecordingEventBus implements EventBus {
        public record Delivery(String scope, String target, ServerEvent event) { }

        public final List<Delivery> deliveries = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void toUser(String username, ServerEvent event) {
            deliveries.add(new Delivery("user", username, event));
        }

        @Override
        public void toBranch(String branchId, ServerEvent event) {
            deliveries.add(new Delivery("branch", branchId, event));
        }

        @Override
        public void toAll(ServerEvent event) {
            deliveries.add(new Delivery("all", null, event));
        }

        public List<ServerEvent> toUser(String username) {
            synchronized (deliveries) {
                return deliveries.stream()
                        .filter(d -> d.scope().equals("user") && d.target().equals(username))
                        .map(Delivery::event).toList();
            }
        }

        public long count(String scope, EventType type) {
            synchronized (deliveries) {
                return deliveries.stream()
                        .filter(d -> d.scope().equals(scope) && d.event().event() == type).count();
            }
        }

        public boolean userGot(String username, EventType type) {
            return toUser(username).stream().anyMatch(e -> e.event() == type);
        }

        public void clear() {
            deliveries.clear();
        }
    }

    public static final class RecordingLogger implements ActionLogger {
        public record Entry(ActionType action, String actor, String details) { }
        public record Chat(ChatMetadata metadata, String transcript) { }

        public final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
        public final List<Chat> chats = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void log(ActionType action, String actor, String details) {
            entries.add(new Entry(action, actor, details));
        }

        @Override
        public void logChat(ChatMetadata metadata, String transcript) {
            chats.add(new Chat(metadata, transcript));
        }

        @Override
        public List<LogEntry> query(ActionType type, LocalDate from, LocalDate to, int limit) {
            synchronized (entries) {
                return entries.stream()
                        .filter(e -> type == null || e.action() == type)
                        .map(e -> new LogEntry("", e.action(), e.actor(), e.details()))
                        .limit(limit)
                        .toList();
            }
        }

        @Override
        public List<String> listTranscripts() {
            synchronized (chats) {
                return chats.stream().filter(c -> c.transcript() != null).map(c -> c.metadata().sessionId()).toList();
            }
        }

        @Override
        public Optional<String> readTranscript(String sessionId) {
            synchronized (chats) {
                return chats.stream()
                        .filter(c -> c.metadata().sessionId().equals(sessionId) && c.transcript() != null)
                        .map(Chat::transcript).findFirst();
            }
        }

        public boolean has(ActionType action) {
            synchronized (entries) {
                return entries.stream().anyMatch(e -> e.action() == action);
            }
        }
    }

    public static final class InMemoryPendingChatDao implements PendingChatDao {
        public final Map<String, ChatRequest> store = new LinkedHashMap<>();

        @Override
        public List<ChatRequest> loadAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void save(ChatRequest request) {
            store.put(request.id(), request);
        }

        @Override
        public void delete(String requestId) {
            store.remove(requestId);
        }
    }
}
