package com.store.server.logging;

import com.store.server.chat.ChatMetadata;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Audit log of business actions. Implementations must be thread-safe and must not block callers for long. */
public interface ActionLogger {

    void log(ActionType action, String actor, String details);

    /**
     * Logs a finished chat's metadata and, when {@code transcript} is not null, saves the transcript.
     */
    void logChat(ChatMetadata metadata, String transcript);

    /**
     * Reads back logged actions, newest first.
     * @param type only this action type, or null for all
     * @param from first day included, or null
     * @param to   last day included, or null
     * @param limit maximum number of entries returned
     */
    List<LogEntry> query(ActionType type, LocalDate from, LocalDate to, int limit);

    /** Session ids of saved chat transcripts, newest first. */
    List<String> listTranscripts();

    /** The saved transcript of a chat, if one was saved. */
    Optional<String> readTranscript(String sessionId);
}
