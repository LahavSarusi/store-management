package com.store.server.controller;

import com.store.server.chat.ChatQueueManager;
import com.store.server.chat.ChatSession;
import com.store.server.dao.BranchDao;
import com.store.server.net.ClientHandler;
import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.Request;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.protocol.dto.Dtos.ChatLeaveRequest;
import com.store.server.protocol.dto.Dtos.ChatRequestIdRequest;
import com.store.server.protocol.dto.Dtos.ChatSendRequest;
import com.store.server.protocol.dto.Dtos.ChatSessionRequest;
import com.store.server.protocol.dto.Dtos.ChatTargetRequest;

import java.util.Map;

import static com.store.server.controller.Controller.body;

/** Inter-branch chat routes; all logic lives in {@link ChatQueueManager}. */
public class ChatController implements Controller {

    private final ChatQueueManager chat;
    private final BranchDao branches;

    public ChatController(ChatQueueManager chat, BranchDao branches) {
        this.chat = chat;
        this.branches = branches;
    }

    @Override
    public void registerRoutes(RequestDispatcher d) {
        d.register(RequestType.CHAT_REQUEST, this::request)
         .register(RequestType.CHAT_ACCEPT, this::accept)
         .register(RequestType.CHAT_DECLINE, (req, c) -> {
             chat.decline(c.requireEmployee(), body(req, ChatRequestIdRequest.class).requestId());
             return Response.ok(req.requestId());
         })
         .register(RequestType.CHAT_SEND, (req, c) -> {
             ChatSendRequest b = body(req, ChatSendRequest.class);
             return Response.ok(req.requestId(), chat.sendMessage(c.requireEmployee(), b.sessionId(), b.text()));
         })
         .register(RequestType.CHAT_JOIN, (req, c) -> {
             chat.join(c.requireEmployee(), body(req, ChatSessionRequest.class).sessionId());
             return Response.ok(req.requestId());
         })
         .register(RequestType.CHAT_LEAVE, (req, c) -> {
             ChatLeaveRequest b = body(req, ChatLeaveRequest.class);
             chat.leave(c.requireEmployee(), b.sessionId(), b.saveTranscript());
             return Response.ok(req.requestId());
         })
         .register(RequestType.CHAT_LIST_ACTIVE, (req, c) -> Response.ok(req.requestId(), chat.listActive()));
    }

    private Response request(Request req, ClientHandler c) {
        String target = body(req, ChatTargetRequest.class).targetBranchId();
        if (target == null || branches.findById(target).isEmpty()) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Unknown branch: " + target);
        }
        return Response.ok(req.requestId(), chat.requestChat(c.requireEmployee(), target));
    }

    private Response accept(Request req, ClientHandler c) {
        ChatSession s = chat.accept(c.requireEmployee(), body(req, ChatRequestIdRequest.class).requestId());
        return Response.ok(req.requestId(), Map.of("sessionId", s.id()));
    }
}
