package com.store.server.model.customer;

import com.store.server.model.SaleLine;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * VIP customer: the cheapest unit is free when buying {@value #FREE_ITEM_MIN_UNITS}+ units,
 * a fixed discount on the rest, and service perks.
 */
public class VipCustomer extends Customer {

    public static final int DISCOUNT_PERCENT = 20;
    public static final int FREE_ITEM_MIN_UNITS = 3;

    public VipCustomer(String id, String fullName, String phone, int purchaseCount, BigDecimal totalSpent) {
        super(id, fullName, phone, purchaseCount, totalSpent);
    }

    @Override
    protected BigDecimal calculateDiscount(BigDecimal subtotal, List<SaleLine> lines) {
        BigDecimal freeItem = BigDecimal.ZERO;
        if (SaleLine.totalUnits(lines) >= FREE_ITEM_MIN_UNITS) {
            freeItem = lines.stream()
                    .map(SaleLine::unitPrice)
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
        }
        return freeItem.add(percentOf(subtotal.subtract(freeItem), DISCOUNT_PERCENT));
    }

    @Override
    protected List<String> extraBenefits(List<SaleLine> lines) {
        return List.of("Free alterations", "Priority fitting room");
    }

    @Override
    public CustomerType getType() {
        return CustomerType.VIP;
    }

    @Override
    public String getTrackDescription() {
        return "Cheapest item free from " + FREE_ITEM_MIN_UNITS + " items, " + DISCOUNT_PERCENT
                + "% off the rest, free alterations, priority fitting room";
    }
}
