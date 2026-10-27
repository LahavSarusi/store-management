package com.store.server.chat;

/** Response to CHAT_REQUEST: connected right away, or queued at {@code position} (1-based). */
public record ChatRequestResult(Status status, String sessionId, String requestId, int position) {

    public enum Status { CONNECTED, QUEUED }

    public static ChatRequestResult connected(String sessionId) {
        return new ChatRequestResult(Status.CONNECTED, sessionId, null, 0);
    }

    public static ChatRequestResult queued(String requestId, int position) {
        return new ChatRequestResult(Status.QUEUED, null, requestId, position);
    }
}
