package com.store.server.chat;

import com.store.server.util.IdGenerator;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A live chat room (Mediator): participants never talk to each other directly; every message goes
 * through the session, which records it and hands it to the manager for delivery.
 * Not thread-safe on its own - guarded by {@link ChatQueueManager}.
 */
public final class ChatSession {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String id;
    private final String initiator;
    private final long startedAt;
    private final Set<String> participants = new LinkedHashSet<>();
    /** Everyone who was ever in the chat, for the log. */
    private final Set<String> everParticipated = new LinkedHashSet<>();
    private final List<ChatMessage> messages = new ArrayList<>();

    private ChatSession(String id, String initiator, long startedAt) {
        this.id = id;
        this.initiator = initiator;
        this.startedAt = startedAt;
    }

    public static ChatSession create(String initiator, String other) {
        ChatSession s = new ChatSession(IdGenerator.next("CHAT"), initiator, System.currentTimeMillis());
        s.addParticipant(initiator);
        s.addParticipant(other);
        return s;
    }

    public String id() {
        return id;
    }

    public void addParticipant(String username) {
        participants.add(username);
        everParticipated.add(username);
    }

    public void removeParticipant(String username) {
        participants.remove(username);
    }

    public boolean hasParticipant(String username) {
        return participants.contains(username);
    }

    public int participantCount() {
        return participants.size();
    }

    /** Snapshot of current participants. */
    public List<String> participants() {
        return List.copyOf(participants);
    }

    public ChatMessage append(String from, String text) {
        ChatMessage m = new ChatMessage(id, from, text, System.currentTimeMillis());
        messages.add(m);
        return m;
    }

    public int messageCount() {
        return messages.size();
    }

    public long startedAt() {
        return startedAt;
    }

    public ChatMetadata metadata() {
        return new ChatMetadata(id, initiator, List.copyOf(everParticipated), startedAt,
                System.currentTimeMillis(), messages.size());
    }

    /** Human-readable transcript for saving to a .txt file. */
    public String transcript() {
        StringBuilder sb = new StringBuilder()
                .append("Chat ").append(id).append('\n')
                .append("Participants: ").append(String.join(", ", everParticipated)).append('\n')
                .append("Started: ").append(format(startedAt)).append('\n')
                .append("----------------------------------------\n");
        for (ChatMessage m : messages) {
            sb.append('[').append(format(m.timestamp())).append("] ")
              .append(m.from()).append(": ").append(m.text()).append('\n');
        }
        return sb.toString();
    }

    private static String format(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(TIME);
    }
}
