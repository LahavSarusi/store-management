package com.store.client.net;

import com.store.client.dto.ErrorCode;

/**
 * The server answered with {@code status: ERROR}, or the connection is gone.
 * {@link #code()} is null for connection problems and for error codes this client doesn't know.
 */
public class ServerException extends RuntimeException {

    private final ErrorCode code;

    public ServerException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static ServerException connectionLost() {
        return new ServerException(null, "The connection to the server was lost.");
    }

    public ErrorCode code() {
        return code;
    }

    public boolean is(ErrorCode expected) {
        return code == expected;
    }
}
