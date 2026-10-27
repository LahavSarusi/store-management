package com.store.server.model.customer;

import java.math.BigDecimal;

/**
 * Factory for the customer hierarchy. Also owns the automatic upgrade rules:
 * NEW -> RETURNING after the first purchase, RETURNING -> VIP once total spend reaches {@link #VIP_SPEND_THRESHOLD}.
 */
public final class CustomerFactory {

    public static final BigDecimal VIP_SPEND_THRESHOLD = new BigDecimal("2000");

    private CustomerFactory() { }

    public static Customer create(CustomerType type, String id, String fullName, String phone) {
        return create(type, id, fullName, phone, 0, BigDecimal.ZERO);
    }

    public static Customer create(CustomerType type, String id, String fullName, String phone,
                                  int purchaseCount, BigDecimal totalSpent) {
        return switch (type) {
            case NEW -> new NewCustomer(id, fullName, phone, purchaseCount, totalSpent);
            case RETURNING -> new ReturningCustomer(id, fullName, phone, purchaseCount, totalSpent);
            case VIP -> new VipCustomer(id, fullName, phone, purchaseCount, totalSpent);
        };
    }

    /** Re-creates the customer as another type, keeping identity and statistics. */
    public static Customer changeType(Customer c, CustomerType newType) {
        if (c.getType() == newType) {
            return c;
        }
        return create(newType, c.getId(), c.getFullName(), c.getPhone(), c.getPurchaseCount(), c.getTotalSpent());
    }

    /** @return the upgraded customer, or the same instance when no upgrade applies. Never downgrades. */
    public static Customer upgradeIfEligible(Customer c) {
        return switch (c.getType()) {
            case NEW -> c.getPurchaseCount() >= 1 ? upgradeIfEligible(changeType(c, CustomerType.RETURNING)) : c;
            case RETURNING -> c.getTotalSpent().compareTo(VIP_SPEND_THRESHOLD) >= 0 ? changeType(c, CustomerType.VIP) : c;
            case VIP -> c;
        };
    }
}
