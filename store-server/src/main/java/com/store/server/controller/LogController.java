package com.store.server.controller;

import com.store.server.logging.ActionLogger;
import com.store.server.net.ClientHandler;
import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.Request;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.protocol.dto.Dtos.ChatSessionRequest;
import com.store.server.protocol.dto.Dtos.LogQueryRequest;
import com.store.server.protocol.dto.Dtos.TranscriptView;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static com.store.server.controller.Controller.body;

/** Log viewer for admins: action log by type/date, and saved chat transcripts. */
public class LogController implements Controller {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    private final ActionLogger logger;

    public LogController(ActionLogger logger) {
        this.logger = logger;
    }

    @Override
    public void registerRoutes(RequestDispatcher d) {
        d.register(RequestType.LOG_LIST, this::list)
         .register(RequestType.CHAT_TRANSCRIPT_LIST, (req, c) -> Response.ok(req.requestId(), logger.listTranscripts()))
         .register(RequestType.CHAT_TRANSCRIPT_GET, this::transcript);
    }

    private Response list(Request req, ClientHandler c) {
        LogQueryRequest q = req.payload() == null
                ? new LogQueryRequest(null, null, null, null)
                : body(req, LogQueryRequest.class);
        int limit = q.limit() == null ? DEFAULT_LIMIT : q.limit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "limit must be 1-" + MAX_LIMIT);
        }
        return Response.ok(req.requestId(),
                logger.query(q.type(), parseDate(q.from(), "from"), parseDate(q.to(), "to"), limit));
    }

    private Response transcript(Request req, ClientHandler c) {
        String sessionId = body(req, ChatSessionRequest.class).sessionId();
        String text = logger.readTranscript(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "No saved transcript for " + sessionId));
        return Response.ok(req.requestId(), new TranscriptView(sessionId, text));
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new DomainException(ErrorCode.BAD_REQUEST, field + " must be yyyy-MM-dd");
        }
    }
}
