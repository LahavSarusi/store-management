package com.store.server.protocol;

/** Unsolicited server-to-client push notifications. */
public enum EventType {
    /** Branch scope: inventory of the receiver's branch changed. */
    INVENTORY_UPDATED,
    /** Global scope: a customer was created, edited or upgraded. */
    CUSTOMER_UPDATED,
    CHAT_STARTED,
    CHAT_MESSAGE,
    /** A queued chat request is waiting for the receiver ("come back to the chat"). */
    CHAT_PENDING_REQUEST,
    CHAT_PARTICIPANT_JOINED,
    CHAT_PARTICIPANT_LEFT,
    CHAT_ENDED,
    /** The server closed this session (account deleted, shutdown). */
    FORCED_LOGOUT
}
