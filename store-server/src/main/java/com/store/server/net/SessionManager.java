package com.store.server.net;

import com.store.server.protocol.EventType;
import com.store.server.protocol.ServerEvent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry of logged-in users. Guarantees a user is logged in from at most one connection:
 * {@link #register} is an atomic put-if-absent, so two simultaneous logins cannot both succeed.
 */
public final class SessionManager {

    private final ConcurrentMap<String, ClientConnection> online = new ConcurrentHashMap<>();

    /** @return false if the username already has a live session */
    public boolean register(String username, ClientConnection connection) {
        return online.putIfAbsent(key(username), connection) == null;
    }

    /** Removes the session only if it belongs to {@code connection} (a stale handler cannot evict a new login). */
    public void unregister(String username, ClientConnection connection) {
        online.remove(key(username), connection);
    }

    public Optional<ClientConnection> get(String username) {
        return Optional.ofNullable(online.get(key(username)));
    }

    public boolean isOnline(String username) {
        return online.containsKey(key(username));
    }

    public Collection<ClientConnection> all() {
        return List.copyOf(online.values());
    }

    /** Sends FORCED_LOGOUT and closes the user's connection, if online. */
    public void forceLogout(String username, String reason) {
        get(username).ifPresent(c -> kick(c, reason));
    }

    public void disconnectAll(String reason) {
        all().forEach(c -> kick(c, reason));
    }

    private static void kick(ClientConnection c, String reason) {
        c.send(ServerEvent.of(EventType.FORCED_LOGOUT, Map.of("reason", reason)));
        c.close();
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
