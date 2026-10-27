package com.store.server.report;

import com.store.server.dao.json.JsonBranchDao;
import com.store.server.dao.json.JsonSaleDao;
import com.store.server.model.Branch;
import com.store.server.model.Category;
import com.store.server.model.Sale;
import com.store.server.model.SaleLine;
import com.store.server.model.customer.CustomerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Jerusalem");

    @TempDir
    Path dir;

    private ReportService service;

    @BeforeEach
    void setUp() {
        JsonBranchDao branches = new JsonBranchDao(dir.resolve("branches.json"));
        branches.save(new Branch("TLV", "Tel Aviv", "Tel Aviv"));
        branches.save(new Branch("HFA", "Haifa", "Haifa"));
        JsonSaleDao sales = new JsonSaleDao(dir.resolve("sales.json"));

        SaleLine shirts = new SaleLine("SH-001", "T-Shirt", Category.SHIRTS, new BigDecimal("50"), 2);   // 100
        SaleLine jeans = new SaleLine("PA-001", "Jeans", Category.PANTS, new BigDecimal("100"), 1);      // 100
        // TLV, Oct 1st: 200 gross, 10% off -> 180 net
        sales.save(sale("S1", "TLV", List.of(shirts, jeans), "200", "180", LocalDate.of(2026, 10, 1)));
        // HFA, Oct 20th: 100 gross, no discount
        sales.save(sale("S2", "HFA", List.of(shirts), "100", "100", LocalDate.of(2026, 10, 20)));

        service = new ReportService(sales, branches, ZONE);
    }

    private static Sale sale(String id, String branch, List<SaleLine> lines, String subtotal, String total, LocalDate day) {
        BigDecimal sub = new BigDecimal(subtotal);
        BigDecimal tot = new BigDecimal(total);
        long ts = day.atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli();
        return new Sale(id, branch, "emp", "1", CustomerType.RETURNING, lines, sub, sub.subtract(tot), tot, ts);
    }

    private static SalesReport.Row row(SalesReport r, String key) {
        return r.rows().stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void groupsByBranchWithNetRevenue() {
        SalesReport r = service.generate(new ReportCriteria(GroupBy.BRANCH, null, null, null, null, null));
        assertEquals(2, r.rows().size());
        assertEquals("Tel Aviv", row(r, "TLV").label());
        assertEquals(0, new BigDecimal("180.00").compareTo(row(r, "TLV").netRevenue()));
        assertEquals(0, new BigDecimal("280.00").compareTo(r.totalNet()));
        assertEquals(0, new BigDecimal("300.00").compareTo(r.totalGross()));
        assertEquals(2, r.totalSales());
    }

    @Test
    void groupsByCategoryAndProduct() {
        SalesReport byCategory = service.generate(new ReportCriteria(GroupBy.CATEGORY, null, null, null, null, null));
        assertEquals(4, row(byCategory, "SHIRTS").units());
        assertEquals(2, row(byCategory, "SHIRTS").sales());

        SalesReport oneProduct = service.generate(new ReportCriteria(GroupBy.PRODUCT, null, List.of("PA-001"), null, null, null));
        assertEquals(1, oneProduct.rows().size());
        assertEquals(0, new BigDecimal("90.00").compareTo(oneProduct.rows().get(0).netRevenue()));   // half of 180
    }

    @Test
    void filtersBySeveralProductsOrCategories() {
        SalesReport twoProducts = service.generate(new ReportCriteria(GroupBy.PRODUCT, null,
                List.of("PA-001", "SH-001"), null, null, null));
        assertEquals(2, twoProducts.rows().size());

        SalesReport pantsOnly = service.generate(new ReportCriteria(GroupBy.CATEGORY, null, null,
                List.of(Category.PANTS, Category.SHOES), null, null));
        assertEquals(1, pantsOnly.rows().size());
        assertEquals("PANTS", pantsOnly.rows().get(0).key());
        assertTrue(pantsOnly.filters().contains("categories=PANTS,SHOES"));
    }

    @Test
    void dailyReportCoversOneDayOnly() throws Exception {
        SalesReport day = service.generate(ReportCriteria.daily(GroupBy.BRANCH, null, null, null, LocalDate.of(2026, 10, 1)));
        assertEquals(1, day.rows().size());
        assertEquals("TLV", day.rows().get(0).key());
        assertEquals("2026-10-01", day.day());

        Path file = new TxtReportExporter(dir.resolve("reports")).export(day);
        assertTrue(file.getFileName().toString().startsWith("daily_sales_by_branch_"));
        assertTrue(Files.readString(file).contains("DAILY SALES REPORT BY BRANCH - 2026-10-01"));
    }

    @Test
    void filtersByBranchAndDateRange() {
        SalesReport tlv = service.generate(new ReportCriteria(GroupBy.PRODUCT, "TLV", null, null, null, null));
        assertEquals(1, tlv.totalSales());

        SalesReport late = service.generate(new ReportCriteria(GroupBy.BRANCH, null, null, null,
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 31)));
        assertEquals(1, late.rows().size());
        assertEquals("HFA", late.rows().get(0).key());
    }

    @Test
    void exportsNotepadFriendlyTextFile() throws Exception {
        SalesReport r = service.generate(new ReportCriteria(GroupBy.CATEGORY, null, null, null, null, null));
        TxtReportExporter exporter = new TxtReportExporter(dir.resolve("reports"));

        Path file = exporter.export(r);

        assertTrue(file.getFileName().toString().matches("sales_by_category_\\d{8}_\\d{6}\\.txt"));
        String text = Files.readString(file);
        assertTrue(text.contains("SALES REPORT BY CATEGORY"));
        assertTrue(text.contains("TOTAL"));
        assertTrue(text.contains("\r\n"), "CRLF line endings for Notepad");
    }
}
