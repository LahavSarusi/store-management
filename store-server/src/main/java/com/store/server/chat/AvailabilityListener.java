package com.store.server.chat;

/** Observer notified whenever an online employee becomes free to chat. */
@FunctionalInterface
public interface AvailabilityListener {
    void onAvailable(String username);
}
