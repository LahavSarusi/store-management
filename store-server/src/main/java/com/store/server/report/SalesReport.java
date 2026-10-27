package com.store.server.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated sales statistics. {@code grossRevenue} is list price x quantity; {@code netRevenue} is what
 * customers actually paid (each sale's discount spread over its lines in proportion to their value).
 * {@code day} (yyyy-MM-dd) is set only for a daily report.
 */
public record SalesReport(GroupBy groupBy,
                          String filters,
                          String day,
                          String generatedAt,
                          List<Row> rows,
                          int totalUnits,
                          int totalSales,
                          BigDecimal totalGross,
                          BigDecimal totalNet) {

    public record Row(String key, String label, int units, int sales, BigDecimal grossRevenue, BigDecimal netRevenue) { }
}
