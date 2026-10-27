package com.store.server.dao;

import com.store.server.chat.ChatRequest;

import java.util.List;

/** Durable store for chat requests that are still waiting for an available employee. */
public interface PendingChatDao {
    /** @return pending requests in creation order */
    List<ChatRequest> loadAll();
    void save(ChatRequest request);
    void delete(String requestId);
}
