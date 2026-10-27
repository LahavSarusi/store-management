package com.store.server.controller;

import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.model.Employee;
import com.store.server.model.Role;
import com.store.server.net.ClientHandler;
import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.Request;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.protocol.dto.Dtos.ReportExport;
import com.store.server.protocol.dto.Dtos.ReportRequest;
import com.store.server.protocol.dto.Dtos.ReportResult;
import com.store.server.report.ReportCriteria;
import com.store.server.report.ReportService;
import com.store.server.report.SalesReport;
import com.store.server.report.TxtReportExporter;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import static com.store.server.controller.Controller.body;

/**
 * Sales statistics. Admins see the whole network; shift managers only their own branch.
 * REPORT_EXPORT saves a .txt under {@code reports/} and also returns its text so the client can save it locally.
 */
public class ReportController implements Controller {

    private final ReportService reports;
    private final TxtReportExporter exporter;
    private final ActionLogger logger;

    public ReportController(ReportService reports, TxtReportExporter exporter, ActionLogger logger) {
        this.reports = reports;
        this.exporter = exporter;
        this.logger = logger;
    }

    @Override
    public void registerRoutes(RequestDispatcher d) {
        d.register(RequestType.REPORT_SALES, this::report)
         .register(RequestType.REPORT_EXPORT, this::export);
    }

    private Response report(Request req, ClientHandler c) {
        SalesReport report = reports.generate(criteria(req, c.requireEmployee()));
        return Response.ok(req.requestId(), new ReportResult(report, exporter.format(report)));
    }

    private Response export(Request req, ClientHandler c) {
        Employee e = c.requireEmployee();
        SalesReport report = reports.generate(criteria(req, e));
        Path file = exporter.export(report);
        logger.log(ActionType.REPORT_EXPORTED, e.getUsername(), file.getFileName() + " (" + report.filters() + ")");
        return Response.ok(req.requestId(), new ReportExport(file.getFileName().toString(), exporter.format(report)));
    }

    private static ReportCriteria criteria(Request req, Employee e) {
        ReportRequest r = req.payload() == null
                ? new ReportRequest(null, null, null, null, null, null, null)
                : body(req, ReportRequest.class);
        String branchId = r.branchId();
        if (e.getRole() != Role.ADMIN) {
            if (branchId != null && !branchId.equals(e.getBranchId())) {
                throw new DomainException(ErrorCode.FORBIDDEN, "You can only see reports of your own branch");
            }
            branchId = e.getBranchId();
        }
        List<String> skus = r.skus() == null ? List.of()
                : r.skus().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();

        LocalDate day = parseDate(r.date(), "date");
        LocalDate from = parseDate(r.from(), "from");
        LocalDate to = parseDate(r.to(), "to");
        if (day != null) {
            if (from != null || to != null) {
                throw new DomainException(ErrorCode.BAD_REQUEST, "Use either 'date' or 'from'/'to', not both");
            }
            return ReportCriteria.daily(r.groupBy(), branchId, skus, r.categories(), day);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "'from' is after 'to'");
        }
        return new ReportCriteria(r.groupBy(), branchId, skus, r.categories(), from, to);
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
