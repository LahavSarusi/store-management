package com.store.server.protocol;

/**
 * Server-to-client answer, correlated to a {@link Request} by {@code requestId}.
 * <pre>
 * { "kind":"RESPONSE", "requestId":"c-17", "status":"OK", "payload":{...} }
 * { "kind":"RESPONSE", "requestId":"c-17", "status":"ERROR", "error":{"code":"FORBIDDEN","message":"..."} }
 * </pre>
 */
public record Response(String kind, String requestId, Status status, Object payload, ErrorBody error) {

    public enum Status { OK, ERROR }

    public record ErrorBody(ErrorCode code, String message) { }

    public static Response ok(String requestId, Object payload) {
        return new Response("RESPONSE", requestId, Status.OK, payload, null);
    }

    public static Response ok(String requestId) {
        return ok(requestId, null);
    }

    public static Response error(String requestId, ErrorCode code, String message) {
        return new Response("RESPONSE", requestId, Status.ERROR, null, new ErrorBody(code, message));
    }

    public boolean isOk() {
        return status == Status.OK;
    }
}
