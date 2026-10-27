package com.store.server.event;

import com.store.server.protocol.ServerEvent;

/**
 * Publish side of the live-sync Observer: services publish events here without knowing about sockets.
 * Subscribers are the connected, logged-in clients, selected by scope.
 */
public interface EventBus {

    /** Deliver to one logged-in user (no-op if offline). */
    void toUser(String username, ServerEvent event);

    /** Deliver to every logged-in employee of a branch. */
    void toBranch(String branchId, ServerEvent event);

    /** Deliver to every logged-in user. */
    void toAll(ServerEvent event);
}
