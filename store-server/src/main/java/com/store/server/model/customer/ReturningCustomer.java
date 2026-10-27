package com.store.server.model.customer;

import com.store.server.model.SaleLine;

import java.math.BigDecimal;
import java.util.List;

/** Returning customer: loyalty discount, increased on large baskets. */
public class ReturningCustomer extends Customer {

    public static final int BASE_DISCOUNT_PERCENT = 10;
    public static final int LARGE_BASKET_DISCOUNT_PERCENT = 15;
    public static final BigDecimal LARGE_BASKET_THRESHOLD = new BigDecimal("500");

    public ReturningCustomer(String id, String fullName, String phone, int purchaseCount, BigDecimal totalSpent) {
        super(id, fullName, phone, purchaseCount, totalSpent);
    }

    @Override
    protected BigDecimal calculateDiscount(BigDecimal subtotal, List<SaleLine> lines) {
        int percent = subtotal.compareTo(LARGE_BASKET_THRESHOLD) >= 0
                ? LARGE_BASKET_DISCOUNT_PERCENT
                : BASE_DISCOUNT_PERCENT;
        return percentOf(subtotal, percent);
    }

    @Override
    public CustomerType getType() {
        return CustomerType.RETURNING;
    }

    @Override
    public String getTrackDescription() {
        return BASE_DISCOUNT_PERCENT + "% off every purchase, " + LARGE_BASKET_DISCOUNT_PERCENT
                + "% from " + LARGE_BASKET_THRESHOLD.toPlainString();
    }
}
