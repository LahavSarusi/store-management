package com.store.client.dto;

/** Error codes in {@code error.code}; mirrors {@code com.store.server.protocol.ErrorCode}. */
public enum ErrorCode {
    BAD_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    ALREADY_LOGGED_IN,
    INVALID_CREDENTIALS,
    NOT_FOUND,
    CONFLICT,
    OUT_OF_STOCK,
    POLICY_VIOLATION,
    SERVER_BUSY,
    INTERNAL
}
