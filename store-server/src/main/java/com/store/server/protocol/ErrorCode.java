package com.store.server.protocol;

/** Machine-readable error codes returned in {@code error.code}. */
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
