package com.store.server.model.customer;

import com.store.server.model.SaleLine;

import java.math.BigDecimal;
import java.util.List;

/** First-time customer: one-off welcome discount and gift on the very first purchase. */
public class NewCustomer extends Customer {

    public static final int WELCOME_DISCOUNT_PERCENT = 5;

    public NewCustomer(String id, String fullName, String phone, int purchaseCount, BigDecimal totalSpent) {
        super(id, fullName, phone, purchaseCount, totalSpent);
    }

    @Override
    protected BigDecimal calculateDiscount(BigDecimal subtotal, List<SaleLine> lines) {
        return isFirstPurchase() ? percentOf(subtotal, WELCOME_DISCOUNT_PERCENT) : BigDecimal.ZERO;
    }

    @Override
    protected List<String> extraBenefits(List<SaleLine> lines) {
        return isFirstPurchase() ? List.of("Welcome gift bag") : List.of();
    }

    private boolean isFirstPurchase() {
        return getPurchaseCount() == 0;
    }

    @Override
    public CustomerType getType() {
        return CustomerType.NEW;
    }

    @Override
    public String getTrackDescription() {
        return WELCOME_DISCOUNT_PERCENT + "% off and a welcome gift on the first purchase";
    }
}
