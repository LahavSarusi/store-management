package com.store.server.net;

import com.store.server.protocol.Request;
import com.store.server.protocol.Response;

/**
 * Command for one route. Handlers may throw {@link com.store.server.protocol.DomainException};
 * the dispatcher turns it into an error response.
 */
@FunctionalInterface
public interface RequestHandler {
    Response handle(Request request, ClientHandler client);
}
