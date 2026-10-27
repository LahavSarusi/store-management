package com.store.server.report;

import com.store.server.dao.BranchDao;
import com.store.server.dao.SaleDao;
import com.store.server.model.Branch;
import com.store.server.model.Sale;
import com.store.server.model.SaleLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds sales statistics by branch, product or category from the recorded sales. */
public class ReportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SaleDao saleDao;
    private final BranchDao branchDao;
    private final ZoneId zone;

    public ReportService(SaleDao saleDao, BranchDao branchDao, ZoneId zone) {
        this.saleDao = saleDao;
        this.branchDao = branchDao;
        this.zone = zone;
    }

    public SalesReport generate(ReportCriteria criteria) {
        Map<String, Accumulator> groups = new LinkedHashMap<>();
        Set<String> allSales = new HashSet<>();

        for (Sale sale : saleDao.findAll()) {
            if (!matchesSale(sale, criteria)) {
                continue;
            }
            for (SaleLine line : sale.lines()) {
                if (!matchesLine(line, criteria)) {
                    continue;
                }
                String key = keyOf(criteria.groupBy(), sale, line);
                groups.computeIfAbsent(key, k -> new Accumulator(labelOf(criteria.groupBy(), sale, line)))
                        .add(sale, line, netOf(sale, line));
                allSales.add(sale.id());
            }
        }

        List<SalesReport.Row> rows = groups.entrySet().stream()
                .map(e -> e.getValue().toRow(e.getKey()))
                .sorted(Comparator.comparing(SalesReport.Row::netRevenue).reversed())
                .toList();
        return new SalesReport(criteria.groupBy(), criteria.describe(),
                criteria.isDaily() ? criteria.from().toString() : null,
                LocalDateTime.now(zone).format(STAMP), rows,
                rows.stream().mapToInt(SalesReport.Row::units).sum(),
                allSales.size(),
                rows.stream().map(SalesReport.Row::grossRevenue).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(SalesReport.Row::netRevenue).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private boolean matchesSale(Sale sale, ReportCriteria c) {
        if (c.branchId() != null && !c.branchId().equals(sale.branchId())) {
            return false;
        }
        LocalDate day = Instant.ofEpochMilli(sale.timestamp()).atZone(zone).toLocalDate();
        return (c.from() == null || !day.isBefore(c.from())) && (c.to() == null || !day.isAfter(c.to()));
    }

    private static boolean matchesLine(SaleLine line, ReportCriteria c) {
        return (c.skus().isEmpty() || c.skus().contains(line.sku()))
                && (c.categories().isEmpty() || c.categories().contains(line.category()));
    }

    private static String keyOf(GroupBy groupBy, Sale sale, SaleLine line) {
        return switch (groupBy) {
            case BRANCH -> sale.branchId();
            case PRODUCT -> line.sku();
            case CATEGORY -> line.category().name();
        };
    }

    private String labelOf(GroupBy groupBy, Sale sale, SaleLine line) {
        return switch (groupBy) {
            case BRANCH -> branchDao.findById(sale.branchId()).map(Branch::name).orElse(sale.branchId());
            case PRODUCT -> line.productName();
            case CATEGORY -> line.category().name();
        };
    }

    /** The line's share of what the customer paid: lineTotal x (total / subtotal). */
    private static BigDecimal netOf(Sale sale, SaleLine line) {
        if (sale.subtotal().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return line.lineTotal().multiply(sale.total()).divide(sale.subtotal(), 2, RoundingMode.HALF_UP);
    }

    private static final class Accumulator {
        private final String label;
        private final Set<String> saleIds = new HashSet<>();
        private int units;
        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal net = BigDecimal.ZERO;

        Accumulator(String label) {
            this.label = label;
        }

        void add(Sale sale, SaleLine line, BigDecimal lineNet) {
            saleIds.add(sale.id());
            units += line.quantity();
            gross = gross.add(line.lineTotal());
            net = net.add(lineNet);
        }

        SalesReport.Row toRow(String key) {
            return new SalesReport.Row(key, label, units, saleIds.size(),
                    gross.setScale(2, RoundingMode.HALF_UP), net.setScale(2, RoundingMode.HALF_UP));
        }
    }
}
