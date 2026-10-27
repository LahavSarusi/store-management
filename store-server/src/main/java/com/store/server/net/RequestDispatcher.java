package com.store.server.net;

import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.Request;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.service.AuthorizationService;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes each request to its {@link RequestHandler} (Command pattern) after the authentication and
 * role checks, and converts exceptions into error responses so a handler can never kill the connection.
 */
public final class RequestDispatcher {

    private static final Logger LOG = Logger.getLogger(RequestDispatcher.class.getName());

    private final Map<RequestType, RequestHandler> handlers = new EnumMap<>(RequestType.class);
    private final AuthorizationService authorization;

    public RequestDispatcher(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    public RequestDispatcher register(RequestType type, RequestHandler handler) {
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException("Duplicate handler for " + type);
        }
        return this;
    }

    public Response dispatch(Request request, ClientHandler client) {
        String id = request.requestId();
        RequestType type = request.type();
        RequestHandler handler = type == null ? null : handlers.get(type);
        if (handler == null) {
            return Response.error(id, ErrorCode.BAD_REQUEST, "Unknown request type");
        }
        if (type != RequestType.LOGIN && client.employee().isEmpty()) {
            return Response.error(id, ErrorCode.UNAUTHENTICATED, "Please log in first");
        }
        if (!authorization.isAllowed(client.employee().orElse(null), type)) {
            return Response.error(id, ErrorCode.FORBIDDEN, "Your role is not allowed to do this");
        }
        try {
            return handler.handle(request, client);
        } catch (DomainException e) {
            return Response.error(id, e.code(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Handler for " + type + " failed", e);
            return Response.error(id, ErrorCode.INTERNAL, "Internal server error");
        }
    }
}
