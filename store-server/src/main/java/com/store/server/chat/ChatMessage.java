package com.store.server.chat;

/** One chat line; also the payload of the CHAT_MESSAGE event. */
public record ChatMessage(String sessionId, String from, String text, long timestamp) { }
