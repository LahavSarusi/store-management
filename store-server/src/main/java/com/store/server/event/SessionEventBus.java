package com.store.server.event;

import com.store.server.net.ClientConnection;
import com.store.server.net.SessionManager;
import com.store.server.protocol.ServerEvent;

/** {@link EventBus} that fans events out to the sessions registered in the {@link SessionManager}. */
public class SessionEventBus implements EventBus {

    private final SessionManager sessions;

    public SessionEventBus(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void toUser(String username, ServerEvent event) {
        sessions.get(username).ifPresent(c -> c.send(event));
    }

    @Override
    public void toBranch(String branchId, ServerEvent event) {
        for (ClientConnection c : sessions.all()) {
            c.employee()
                    .filter(e -> branchId.equals(e.getBranchId()))
                    .ifPresent(e -> c.send(event));
        }
    }

    @Override
    public void toAll(ServerEvent event) {
        sessions.all().forEach(c -> c.send(event));
    }
}
