package com.store.server.chat;

import com.store.server.dao.PendingChatDao;
import com.store.server.event.EventBus;
import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.model.Employee;
import com.store.server.model.Permission;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.EventType;
import com.store.server.protocol.ServerEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Queue and call reception for inter-branch 1-on-1 chats.
 *
 * <p>Patterns:
 * <ul>
 *   <li><b>Observer</b> - every transition to {@link AvailabilityStatus#AVAILABLE} notifies the registered
 *       {@link AvailabilityListener}s; this manager is itself a listener and uses the callback to hand the next
 *       queued request to the employee who just became free.</li>
 *   <li><b>Command</b> - a {@link ChatRequest} captures "connect X with someone in branch B"; it is queued
 *       (and persisted) when nobody is free and executed later when a target employee accepts.</li>
 *   <li><b>Mediator</b> - {@link ChatSession} routes all messages between participants.</li>
 *   <li><b>State</b> - {@link AvailabilityStatus} per online employee.</li>
 * </ul>
 *
 * <p>Flow: CHAT_REQUEST -> free employee in target branch? start session : enqueue (FIFO per branch) + persist.
 * When an employee of that branch becomes available they receive CHAT_PENDING_REQUEST; CHAT_ACCEPT starts the
 * session. A shift manager can CHAT_JOIN any active session.
 *
 * <p>Thread-safety: all state is guarded by {@code this}. Operations are short and outbound sends only append
 * to a buffered socket writer, which is acceptable at store-network scale.
 */
public final class ChatQueueManager implements AvailabilityListener {

    private final Map<String, Employee> online = new HashMap<>();
    private final Map<String, AvailabilityStatus> status = new HashMap<>();
    /** Waiting requests per target branch, oldest first. */
    private final Map<String, Deque<ChatRequest>> queueByBranch = new HashMap<>();
    /** Requests that were offered to an employee and await CHAT_ACCEPT: requestId -> request. */
    private final Map<String, ChatRequest> offered = new HashMap<>();
    /** requestId -> username the request was offered to. */
    private final Map<String, String> offeredTo = new HashMap<>();
    private final Map<String, ChatSession> sessions = new HashMap<>();
    private final List<AvailabilityListener> listeners = new CopyOnWriteArrayList<>();

    private final EventBus bus;
    private final PendingChatDao pendingDao;
    private final ActionLogger logger;

    public ChatQueueManager(EventBus bus, PendingChatDao pendingDao, ActionLogger logger) {
        this.bus = bus;
        this.pendingDao = pendingDao;
        this.logger = logger;
        listeners.add(this);
        // Requests saved before a restart are still waiting.
        pendingDao.loadAll().forEach(r -> queueFor(r.targetBranchId()).addLast(r));
    }

    /** Registers an extra observer of availability changes (e.g. for metrics or a dashboard). */
    public void addAvailabilityListener(AvailabilityListener listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------------ reception

    /** Connects the requester with a free employee of {@code targetBranchId}, or queues the request. */
    public synchronized ChatRequestResult requestChat(Employee requester, String targetBranchId) {
        String user = requester.getUsername();
        if (targetBranchId == null || targetBranchId.equals(requester.getBranchId())) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Chat must be with another branch");
        }
        if (status.get(user) == AvailabilityStatus.IN_CHAT) {
            throw new DomainException(ErrorCode.CONFLICT, "You are already in a chat");
        }

        Optional<Employee> target = findAvailable(targetBranchId);
        if (target.isPresent()) {
            return ChatRequestResult.connected(startSession(requester, target.get()).id());
        }

        Deque<ChatRequest> queue = queueFor(targetBranchId);
        int position = 1;
        for (ChatRequest r : queue) {
            if (r.requesterUsername().equals(user)) {
                return ChatRequestResult.queued(r.id(), position);   // already waiting - don't queue twice
            }
            position++;
        }
        ChatRequest request = ChatRequest.create(requester, targetBranchId);
        queue.addLast(request);
        pendingDao.save(request);
        logger.log(ActionType.CHAT_QUEUED, user, "request=" + request.id() + " targetBranch=" + targetBranchId);
        return ChatRequestResult.queued(request.id(), queue.size());
    }

    /** Executes a pending request that was offered to {@code acceptor} via CHAT_PENDING_REQUEST. */
    public synchronized ChatSession accept(Employee acceptor, String requestId) {
        ChatRequest request = offered.get(requestId);
        if (request == null || !acceptor.getUsername().equals(offeredTo.get(requestId))) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Chat request is no longer pending");
        }
        withdrawOffer(requestId);
        if (status.get(acceptor.getUsername()) != AvailabilityStatus.AVAILABLE) {
            queueFor(request.targetBranchId()).addFirst(request);
            throw new DomainException(ErrorCode.CONFLICT, "You are already in a chat");
        }
        Employee requester = online.get(request.requesterUsername());
        if (requester == null || status.get(requester.getUsername()) != AvailabilityStatus.AVAILABLE) {
            // Keep the request; it is offered again when someone in this branch next becomes available.
            queueFor(request.targetBranchId()).addFirst(request);
            throw new DomainException(ErrorCode.CONFLICT, "The requester is not available right now");
        }
        pendingDao.delete(request.id());
        return startSession(requester, acceptor);
    }

    /** Declines an offered request; it goes back to the front of the queue and is offered to a colleague. */
    public synchronized void decline(Employee e, String requestId) {
        ChatRequest request = offered.get(requestId);
        if (request == null || !e.getUsername().equals(offeredTo.get(requestId))) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Chat request is no longer pending");
        }
        withdrawOffer(requestId);
        queueFor(request.targetBranchId()).addFirst(request);
        online.values().stream()
                .filter(x -> !x.getUsername().equals(e.getUsername()))
                .filter(x -> request.targetBranchId().equals(x.getBranchId()))
                .filter(x -> status.get(x.getUsername()) == AvailabilityStatus.AVAILABLE)
                .map(Employee::getUsername)
                .toList()
                .forEach(this::onAvailable);
    }

    /** A shift manager joins an existing chat as a third participant. */
    public synchronized void join(Employee manager, String sessionId) {
        if (!manager.getRole().has(Permission.SUPERVISE_CHAT)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only shift managers can join a chat");
        }
        ChatSession s = requireSession(sessionId);
        if (s.hasParticipant(manager.getUsername())) {
            throw new DomainException(ErrorCode.CONFLICT, "Already in this chat");
        }
        if (status.get(manager.getUsername()) == AvailabilityStatus.IN_CHAT) {
            throw new DomainException(ErrorCode.CONFLICT, "Leave your current chat first");
        }
        s.addParticipant(manager.getUsername());
        setStatus(manager.getUsername(), AvailabilityStatus.IN_CHAT);
        requeueOffersTo(manager.getUsername());
        logger.log(ActionType.CHAT_JOINED, manager.getUsername(), "session=" + sessionId);
        broadcast(s, EventType.CHAT_PARTICIPANT_JOINED,
                Map.of("sessionId", sessionId, "user", manager.getUsername(), "participants", s.participants()));
    }

    public synchronized ChatMessage sendMessage(Employee from, String sessionId, String text) {
        ChatSession s = requireSession(sessionId);
        if (!s.hasParticipant(from.getUsername())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "You are not in this chat");
        }
        if (text == null || text.isBlank()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Message is empty");
        }
        ChatMessage m = s.append(from.getUsername(), text.strip());
        broadcast(s, EventType.CHAT_MESSAGE, m);
        return m;
    }

    /** Leaves a chat; the chat ends when fewer than two participants remain. */
    public synchronized void leave(Employee e, String sessionId, boolean saveTranscript) {
        ChatSession s = requireSession(sessionId);
        if (!s.hasParticipant(e.getUsername())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "You are not in this chat");
        }
        s.removeParticipant(e.getUsername());
        if (s.participantCount() < 2) {
            endSession(s, saveTranscript);
        } else {
            broadcast(s, EventType.CHAT_PARTICIPANT_LEFT,
                    Map.of("sessionId", sessionId, "user", e.getUsername(), "participants", s.participants()));
        }
        setStatus(e.getUsername(), AvailabilityStatus.AVAILABLE);   // may trigger a pending-request offer
    }

    public synchronized List<ChatSummary> listActive() {
        List<ChatSummary> result = new ArrayList<>();
        for (ChatSession s : sessions.values()) {
            result.add(new ChatSummary(s.id(), s.participants(), s.startedAt(), s.messageCount()));
        }
        return result;
    }

    // ------------------------------------------------------------------ presence

    /** Called after a successful login (after the LOGIN response was sent). */
    public synchronized void onLogin(Employee e) {
        if (!e.getRole().has(Permission.CHAT)) {
            return;   // e.g. admins do not take chats
        }
        online.put(e.getUsername(), e);
        setStatus(e.getUsername(), AvailabilityStatus.AVAILABLE);
    }

    /** Called on logout or dropped connection. Pending requests made by this user stay queued. */
    public synchronized void onDisconnect(Employee e) {
        String user = e.getUsername();
        if (online.remove(user) == null) {
            return;
        }
        for (ChatSession s : List.copyOf(sessions.values())) {
            if (s.hasParticipant(user)) {
                leave(e, s.id(), false);
            }
        }
        requeueOffersTo(user);
        status.put(user, AvailabilityStatus.OFFLINE);
    }

    public synchronized AvailabilityStatus statusOf(String username) {
        return status.getOrDefault(username, AvailabilityStatus.OFFLINE);
    }

    // ------------------------------------------------------------------ Observer callback

    /** Offers the oldest request waiting for this employee's branch, one offer at a time. */
    @Override
    public synchronized void onAvailable(String username) {
        Employee e = online.get(username);
        if (e == null || offeredTo.containsValue(username)) {
            return;
        }
        ChatRequest next = queueFor(e.getBranchId()).pollFirst();
        if (next == null) {
            return;
        }
        offered.put(next.id(), next);
        offeredTo.put(next.id(), username);
        bus.toUser(username, ServerEvent.of(EventType.CHAT_PENDING_REQUEST, Map.of(
                "requestId", next.id(),
                "from", next.requesterUsername(),
                "fromBranch", next.requesterBranchId(),
                "requestedAt", next.createdAt())));
    }

    // ------------------------------------------------------------------ internals

    private void setStatus(String username, AvailabilityStatus newStatus) {
        status.put(username, newStatus);
        if (newStatus == AvailabilityStatus.AVAILABLE) {
            listeners.forEach(l -> l.onAvailable(username));   // re-entrant monitor, safe for this
        }
    }

    private Optional<Employee> findAvailable(String branchId) {
        return online.values().stream()
                .filter(x -> branchId.equals(x.getBranchId()))
                .filter(x -> status.get(x.getUsername()) == AvailabilityStatus.AVAILABLE)
                .filter(x -> !offeredTo.containsValue(x.getUsername()))   // don't skip the queue
                .findFirst();
    }

    private ChatSession startSession(Employee requester, Employee target) {
        ChatSession s = ChatSession.create(requester.getUsername(), target.getUsername());
        sessions.put(s.id(), s);
        for (String user : s.participants()) {
            setStatus(user, AvailabilityStatus.IN_CHAT);
            requeueOffersTo(user);
        }
        logger.log(ActionType.CHAT_STARTED, requester.getUsername(),
                "session=" + s.id() + " with=" + target.getUsername()
                        + " branches=" + requester.getBranchId() + "->" + target.getBranchId());
        broadcast(s, EventType.CHAT_STARTED, Map.of("sessionId", s.id(), "participants", s.participants()));
        return s;
    }

    private void endSession(ChatSession s, boolean saveTranscript) {
        sessions.remove(s.id());
        broadcast(s, EventType.CHAT_ENDED, Map.of("sessionId", s.id()));
        logger.logChat(s.metadata(), saveTranscript ? s.transcript() : null);
        for (String user : s.participants()) {
            s.removeParticipant(user);
            setStatus(user, AvailabilityStatus.AVAILABLE);
        }
    }

    /** Puts any request offered to {@code username} back at the front of its queue. */
    private void requeueOffersTo(String username) {
        Iterator<Map.Entry<String, String>> it = offeredTo.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            if (entry.getValue().equals(username)) {
                ChatRequest r = offered.remove(entry.getKey());
                it.remove();
                queueFor(r.targetBranchId()).addFirst(r);
            }
        }
    }

    private void withdrawOffer(String requestId) {
        offered.remove(requestId);
        offeredTo.remove(requestId);
    }

    private void broadcast(ChatSession s, EventType type, Object payload) {
        ServerEvent event = ServerEvent.of(type, payload);
        s.participants().forEach(u -> bus.toUser(u, event));
    }

    private Deque<ChatRequest> queueFor(String branchId) {
        return queueByBranch.computeIfAbsent(branchId, k -> new ArrayDeque<>());
    }

    private ChatSession requireSession(String sessionId) {
        ChatSession s = sessions.get(sessionId);
        if (s == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Chat not found");
        }
        return s;
    }
}
