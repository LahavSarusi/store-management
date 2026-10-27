package com.store.server.chat;

import com.store.server.model.Employee;
import com.store.server.util.IdGenerator;

/**
 * A request to chat with someone in another branch (Command object): created on CHAT_REQUEST,
 * queued and persisted while no-one is available, executed when a target employee accepts.
 */
public record ChatRequest(String id,
                          String requesterUsername,
                          String requesterBranchId,
                          String targetBranchId,
                          long createdAt) {

    public static ChatRequest create(Employee requester, String targetBranchId) {
        return new ChatRequest(IdGenerator.next("REQ"), requester.getUsername(), requester.getBranchId(),
                targetBranchId, System.currentTimeMillis());
    }
}
