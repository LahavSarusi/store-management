package com.store.server.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Renders a {@link SalesReport} as a fixed-width plain-text table and saves it as a Notepad-friendly .txt file. */
public class TxtReportExporter {

    /** Windows line endings so the file displays correctly in every version of Notepad. */
    private static final String NL = "\r\n";
    private static final String ROW_FORMAT = "%-28s %8s %8s %14s %14s" + NL;
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path reportsDir;

    public TxtReportExporter(Path reportsDir) {
        this.reportsDir = reportsDir;
    }

    public String format(SalesReport report) {
        StringBuilder sb = new StringBuilder();
        String title = report.day() == null
                ? "SALES REPORT BY " + report.groupBy()
                : "DAILY SALES REPORT BY " + report.groupBy() + " - " + report.day();
        sb.append(title).append(NL).append("=".repeat(title.length())).append(NL)
          .append("Generated: ").append(report.generatedAt()).append(NL)
          .append("Filters:   ").append(report.filters()).append(NL).append(NL);

        sb.append(String.format(Locale.ROOT, ROW_FORMAT, report.groupBy(), "Units", "Sales", "Gross", "Net"));
        sb.append("-".repeat(76)).append(NL);
        for (SalesReport.Row r : report.rows()) {
            sb.append(String.format(Locale.ROOT, ROW_FORMAT, truncate(r.label(), 28),
                    r.units(), r.sales(), r.grossRevenue().toPlainString(), r.netRevenue().toPlainString()));
        }
        if (report.rows().isEmpty()) {
            sb.append("(no sales match these filters)").append(NL);
        }
        sb.append("-".repeat(76)).append(NL);
        sb.append(String.format(Locale.ROOT, ROW_FORMAT, "TOTAL", report.totalUnits(), report.totalSales(),
                report.totalGross().toPlainString(), report.totalNet().toPlainString()));
        return sb.toString();
    }

    /** @return the written file */
    public Path export(SalesReport report) {
        String name = (report.day() == null ? "" : "daily_")
                + "sales_by_" + report.groupBy().name().toLowerCase(Locale.ROOT) + "_"
                + LocalDateTime.now().format(FILE_STAMP) + ".txt";
        Path file = reportsDir.resolve(name);
        try {
            Files.createDirectories(reportsDir);
            Files.writeString(file, format(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write report " + file, e);
        }
        return file;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
