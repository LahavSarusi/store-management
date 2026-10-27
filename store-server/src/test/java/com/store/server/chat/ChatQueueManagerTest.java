package com.store.server.chat;

import com.store.server.TestSupport.InMemoryPendingChatDao;
import com.store.server.TestSupport.RecordingEventBus;
import com.store.server.TestSupport.RecordingLogger;
import com.store.server.model.Employee;
import com.store.server.model.Role;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.EventType;
import com.store.server.protocol.ServerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.store.server.TestSupport.employee;
import static org.junit.jupiter.api.Assertions.*;

class ChatQueueManagerTest {

    private final Employee tlvCashier = employee("tlv.cashier", "TLV", Role.CASHIER);
    private final Employee tlvManager = employee("tlv.manager", "TLV", Role.SHIFT_MANAGER);
    private final Employee hfaSales = employee("hfa.sales", "HFA", Role.SALESPERSON);
    private final Employee hfaCashier = employee("hfa.cashier", "HFA", Role.CASHIER);

    private RecordingEventBus bus;
    private RecordingLogger logger;
    private InMemoryPendingChatDao pending;
    private ChatQueueManager chat;

    @BeforeEach
    void setUp() {
        bus = new RecordingEventBus();
        logger = new RecordingLogger();
        pending = new InMemoryPendingChatDao();
        chat = new ChatQueueManager(bus, pending, logger);
    }

    @Test
    void connectsImmediatelyWhenSomeoneInTargetBranchIsFree() {
        chat.onLogin(tlvCashier);
        chat.onLogin(hfaSales);

        ChatRequestResult r = chat.requestChat(tlvCashier, "HFA");

        assertEquals(ChatRequestResult.Status.CONNECTED, r.status());
        assertTrue(bus.userGot("tlv.cashier", EventType.CHAT_STARTED));
        assertTrue(bus.userGot("hfa.sales", EventType.CHAT_STARTED));
        assertEquals(AvailabilityStatus.IN_CHAT, chat.statusOf("hfa.sales"));
    }

    @Test
    void queuesWhenNobodyIsFreeAndNotifiesTargetWhenTheyBecomeAvailable() {
        chat.onLogin(tlvCashier);
        ChatRequestResult r = chat.requestChat(tlvCashier, "HFA");
        assertEquals(ChatRequestResult.Status.QUEUED, r.status());
        assertEquals(1, r.position());
        assertEquals(1, pending.store.size(), "queued request must be saved on the server");

        chat.onLogin(hfaSales);   // becomes available -> receives the pending request

        ServerEvent offer = bus.toUser("hfa.sales").stream()
                .filter(e -> e.event() == EventType.CHAT_PENDING_REQUEST).findFirst().orElseThrow();
        assertEquals(r.requestId(), ((Map<?, ?>) offer.payload()).get("requestId"));

        ChatSession s = chat.accept(hfaSales, r.requestId());
        assertTrue(s.hasParticipant("tlv.cashier") && s.hasParticipant("hfa.sales"));
        assertTrue(pending.store.isEmpty());
        assertTrue(bus.userGot("tlv.cashier", EventType.CHAT_STARTED));
    }

    @Test
    void requestsAreServedInFifoOrder() {
        chat.onLogin(tlvCashier);
        chat.onLogin(tlvManager);
        ChatRequestResult first = chat.requestChat(tlvCashier, "HFA");
        ChatRequestResult second = chat.requestChat(tlvManager, "HFA");
        assertEquals(2, second.position());

        chat.onLogin(hfaSales);
        chat.onLogin(hfaCashier);

        assertEquals(first.requestId(), offeredRequestId("hfa.sales"));
        assertEquals(second.requestId(), offeredRequestId("hfa.cashier"));
    }

    @Test
    void duplicateRequestReturnsExistingPlaceInQueue() {
        chat.onLogin(tlvCashier);
        ChatRequestResult a = chat.requestChat(tlvCashier, "HFA");
        ChatRequestResult b = chat.requestChat(tlvCashier, "HFA");
        assertEquals(a.requestId(), b.requestId());
        assertEquals(1, pending.store.size());
    }

    @Test
    void chatMustBeInterBranch() {
        chat.onLogin(tlvCashier);
        DomainException e = assertThrows(DomainException.class, () -> chat.requestChat(tlvCashier, "TLV"));
        assertEquals(ErrorCode.BAD_REQUEST, e.code());
    }

    @Test
    void shiftManagerCanJoinButCashierCannot() {
        String id = startChat();
        chat.onLogin(tlvManager);

        chat.join(tlvManager, id);
        assertTrue(bus.userGot("hfa.sales", EventType.CHAT_PARTICIPANT_JOINED));
        assertEquals(1, chat.listActive().size());
        assertEquals(3, chat.listActive().get(0).participants().size());

        chat.onLogin(hfaCashier);
        DomainException e = assertThrows(DomainException.class, () -> chat.join(hfaCashier, id));
        assertEquals(ErrorCode.FORBIDDEN, e.code());
    }

    @Test
    void messagesReachAllParticipantsOnly() {
        String id = startChat();
        chat.onLogin(hfaCashier);
        bus.clear();

        chat.sendMessage(tlvCashier, id, "Do you have SH-002 in size M?");

        assertTrue(bus.userGot("tlv.cashier", EventType.CHAT_MESSAGE));
        assertTrue(bus.userGot("hfa.sales", EventType.CHAT_MESSAGE));
        assertFalse(bus.userGot("hfa.cashier", EventType.CHAT_MESSAGE));
        assertThrows(DomainException.class, () -> chat.sendMessage(hfaCashier, id, "hi"));
    }

    @Test
    void leavingEndsChatLogsItAndFreesTheOtherSideForTheNextRequest() {
        String id = startChat();
        chat.sendMessage(tlvCashier, id, "hello");

        chat.onLogin(tlvManager);
        ChatRequestResult waiting = chat.requestChat(tlvManager, "HFA");   // hfa.sales busy -> queued
        assertEquals(ChatRequestResult.Status.QUEUED, waiting.status());

        chat.leave(tlvCashier, id, true);

        assertTrue(bus.userGot("hfa.sales", EventType.CHAT_ENDED));
        assertEquals(1, logger.chats.size());
        assertNotNull(logger.chats.get(0).transcript());
        assertTrue(logger.chats.get(0).transcript().contains("hello"));
        assertEquals(waiting.requestId(), offeredRequestId("hfa.sales"));
    }

    @Test
    void transcriptIsNotSavedUnlessRequested() {
        String id = startChat();
        chat.leave(hfaSales, id, false);
        assertNull(logger.chats.get(0).transcript());
        assertEquals(2, logger.chats.get(0).metadata().participants().size());
    }

    @Test
    void disconnectEndsChatAndKeepsOwnQueuedRequests() {
        String id = startChat();
        chat.onDisconnect(hfaSales);
        assertTrue(bus.userGot("tlv.cashier", EventType.CHAT_ENDED));
        assertEquals(AvailabilityStatus.AVAILABLE, chat.statusOf("tlv.cashier"));
        assertEquals(AvailabilityStatus.OFFLINE, chat.statusOf("hfa.sales"));
        assertTrue(chat.listActive().isEmpty());
        assertThrows(DomainException.class, () -> chat.sendMessage(tlvCashier, id, "still there?"));
    }

    @Test
    void pendingRequestsSurviveServerRestart() {
        chat.onLogin(tlvCashier);
        ChatRequestResult r = chat.requestChat(tlvCashier, "HFA");

        ChatQueueManager restarted = new ChatQueueManager(bus, pending, logger);
        restarted.onLogin(tlvCashier);
        restarted.onLogin(hfaSales);

        assertEquals(r.requestId(), offeredRequestId("hfa.sales"));
        assertNotNull(restarted.accept(hfaSales, r.requestId()));
    }

    @Test
    void declinedRequestIsOfferedToAColleague() {
        chat.onLogin(tlvCashier);
        ChatRequestResult r = chat.requestChat(tlvCashier, "HFA");
        chat.onLogin(hfaSales);
        chat.onLogin(hfaCashier);   // hfa.sales already holds the offer, nothing for hfa.cashier yet
        assertNull(offeredRequestId("hfa.cashier"));

        chat.decline(hfaSales, r.requestId());

        assertEquals(r.requestId(), offeredRequestId("hfa.cashier"));
        assertThrows(DomainException.class, () -> chat.accept(hfaSales, r.requestId()));
    }

    @Test
    void acceptFailsGracefullyWhenRequesterLeftAndRequestStaysQueued() {
        chat.onLogin(tlvCashier);
        ChatRequestResult r = chat.requestChat(tlvCashier, "HFA");
        chat.onDisconnect(tlvCashier);
        chat.onLogin(hfaSales);

        DomainException e = assertThrows(DomainException.class, () -> chat.accept(hfaSales, r.requestId()));
        assertEquals(ErrorCode.CONFLICT, e.code());
        assertEquals(1, pending.store.size());
    }

    /** Starts tlv.cashier <-> hfa.sales and returns the session id. */
    private String startChat() {
        chat.onLogin(tlvCashier);
        chat.onLogin(hfaSales);
        return chat.requestChat(tlvCashier, "HFA").sessionId();
    }

    private String offeredRequestId(String username) {
        return bus.toUser(username).stream()
                .filter(e -> e.event() == EventType.CHAT_PENDING_REQUEST)
                .map(e -> (String) ((Map<?, ?>) e.payload()).get("requestId"))
                .reduce((a, b) -> b)   // latest offer
                .orElse(null);
    }
}
