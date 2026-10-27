package com.store.server.net;

import com.store.server.model.Employee;
import com.store.server.protocol.EventType;
import com.store.server.protocol.ServerEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    /** Minimal connection that records what it was sent. */
    static final class FakeConnection implements ClientConnection {
        final List<Object> sent = new ArrayList<>();
        boolean closed;

        @Override public Optional<Employee> employee() { return Optional.empty(); }
        @Override public synchronized void send(Object message) { sent.add(message); }
        @Override public void close() { closed = true; }
    }

    @Test
    void sameUserCanLoginOnlyOnceEvenUnderConcurrency() throws Exception {
        SessionManager sessions = new SessionManager();
        int threads = 32;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    if (sessions.register("tlv.cashier", new FakeConnection())) {
                        successes.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, successes.get());
    }

    @Test
    void usernamesAreCaseInsensitive() {
        SessionManager sessions = new SessionManager();
        assertTrue(sessions.register("Dana", new FakeConnection()));
        assertFalse(sessions.register("dana", new FakeConnection()));
    }

    @Test
    void staleConnectionCannotRemoveSomeoneElsesSession() {
        SessionManager sessions = new SessionManager();
        FakeConnection current = new FakeConnection();
        sessions.register("u", current);
        sessions.unregister("u", new FakeConnection());
        assertTrue(sessions.isOnline("u"));
        sessions.unregister("u", current);
        assertFalse(sessions.isOnline("u"));
    }

    @Test
    void forceLogoutNotifiesAndCloses() {
        SessionManager sessions = new SessionManager();
        FakeConnection c = new FakeConnection();
        sessions.register("u", c);
        sessions.forceLogout("u", "bye");
        assertTrue(c.closed);
        assertEquals(EventType.FORCED_LOGOUT, ((ServerEvent) c.sent.get(0)).event());
    }
}
