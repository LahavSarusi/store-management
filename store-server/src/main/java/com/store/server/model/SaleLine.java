package com.store.server.model;

import java.math.BigDecimal;
import java.util.List;

/** One product line in a sale, with the price frozen at sale time. */
public record SaleLine(String sku, String productName, Category category, BigDecimal unitPrice, int quantity) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public static BigDecimal sum(List<SaleLine> lines) {
        return lines.stream().map(SaleLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static int totalUnits(List<SaleLine> lines) {
        return lines.stream().mapToInt(SaleLine::quantity).sum();
    }
}
