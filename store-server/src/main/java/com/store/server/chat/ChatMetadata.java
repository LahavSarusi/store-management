package com.store.server.chat;

import java.util.List;

/** What gets logged about every chat, whether or not the transcript is saved. */
public record ChatMetadata(String sessionId,
                           String initiator,
                           List<String> participants,
                           long startedAt,
                           long endedAt,
                           int messageCount) { }
