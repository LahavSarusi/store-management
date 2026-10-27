package com.store.server.report;

import com.store.server.model.Category;

import java.time.LocalDate;
import java.util.List;

/**
 * Report parameters. Every filter is optional: a null branch/date or an empty list means "no filter".
 * Dates are inclusive; {@code from == to} is a daily report.
 */
public record ReportCriteria(GroupBy groupBy, String branchId, List<String> skus, List<Category> categories,
                             LocalDate from, LocalDate to) {

    public ReportCriteria {
        groupBy = groupBy == null ? GroupBy.BRANCH : groupBy;
        skus = skus == null ? List.of() : List.copyOf(skus);
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /** Criteria for a single day. */
    public static ReportCriteria daily(GroupBy groupBy, String branchId, List<String> skus,
                                       List<Category> categories, LocalDate day) {
        return new ReportCriteria(groupBy, branchId, skus, categories, day, day);
    }

    public boolean isDaily() {
        return from != null && from.equals(to);
    }

    /** Human-readable description of the active filters, e.g. {@code branch=TLV, categories=SHIRTS,PANTS}. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        append(sb, "branch", branchId);
        append(sb, "products", skus.isEmpty() ? null : String.join(",", skus));
        append(sb, "categories", categories.isEmpty() ? null
                : String.join(",", categories.stream().map(Category::name).toList()));
        if (isDaily()) {
            append(sb, "day", from.toString());
        } else if (from != null || to != null) {
            append(sb, "dates", (from == null ? "..." : from.toString()) + ".." + (to == null ? "..." : to.toString()));
        }
        return sb.length() == 0 ? "all sales" : sb.toString();
    }

    private static void append(StringBuilder sb, String key, String value) {
        if (value != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(key).append('=').append(value);
        }
    }
}
