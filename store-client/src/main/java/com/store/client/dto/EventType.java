package com.store.client.dto;

/** Unsolicited server pushes; mirrors {@code com.store.server.protocol.EventType}. */
public enum EventType {
    /** Branch scope: inventory of the user's branch changed. */
    INVENTORY_UPDATED,
    /** Global scope: a customer was created, edited or upgraded. */
    CUSTOMER_UPDATED,
    CHAT_STARTED,
    CHAT_MESSAGE,
    /** A queued chat request is offered to this user. */
    CHAT_PENDING_REQUEST,
    CHAT_PARTICIPANT_JOINED,
    CHAT_PARTICIPANT_LEFT,
    CHAT_ENDED,
    /** The server closed this session (account changed or deleted, shutdown). */
    FORCED_LOGOUT
}
