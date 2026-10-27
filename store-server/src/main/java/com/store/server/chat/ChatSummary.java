package com.store.server.chat;

import java.util.List;

/** Active-chat listing for shift managers (CHAT_LIST_ACTIVE). */
public record ChatSummary(String sessionId, List<String> participants, long startedAt, int messageCount) { }
