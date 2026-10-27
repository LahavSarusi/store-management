package com.store.server.controller;

import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.JsonCodec;
import com.store.server.protocol.Request;

/**
 * A group of related routes. Controllers are thin: parse the payload, take identity and branch from the
 * session, call a service, wrap the result. Authorization has already been checked by the dispatcher.
 */
public interface Controller {

    void registerRoutes(RequestDispatcher dispatcher);

    /** Parses the request payload into a DTO (BAD_REQUEST when missing or malformed). */
    static <T> T body(Request request, Class<T> type) {
        return JsonCodec.fromPayload(request.payload(), type);
    }
}
