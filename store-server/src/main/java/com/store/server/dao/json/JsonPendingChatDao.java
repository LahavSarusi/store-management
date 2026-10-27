package com.store.server.dao.json;

import com.store.server.chat.ChatRequest;
import com.store.server.dao.PendingChatDao;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class JsonPendingChatDao extends JsonCollectionDao<ChatRequest> implements PendingChatDao {

    public JsonPendingChatDao(Path file) {
        super(file, ChatRequest.class, ChatRequest::id);
    }

    @Override
    public List<ChatRequest> loadAll() {
        List<ChatRequest> all = values();
        all.sort(Comparator.comparingLong(ChatRequest::createdAt));
        return all;
    }

    @Override
    public void save(ChatRequest request) {
        put(request);
    }

    @Override
    public void delete(String requestId) {
        remove(requestId);
    }
}
