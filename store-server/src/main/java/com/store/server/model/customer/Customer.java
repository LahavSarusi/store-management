package com.store.server.model.customer;

import com.store.server.model.PurchaseResult;
import com.store.server.model.SaleLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Base customer. Each {@link CustomerType} has its own subclass that supplies the discount rule
 * and benefits for its purchasing route (polymorphism + Template Method).
 * Use {@link CustomerFactory} to create instances or change a customer's type.
 */
public abstract class Customer {

    private final String id;
    private String fullName;
    private String phone;
    private int purchaseCount;
    private BigDecimal totalSpent;

    protected Customer(String id, String fullName, String phone, int purchaseCount, BigDecimal totalSpent) {
        this.id = Objects.requireNonNull(id, "id");
        this.fullName = fullName;
        this.phone = phone;
        this.purchaseCount = purchaseCount;
        this.totalSpent = totalSpent == null ? BigDecimal.ZERO : totalSpent;
    }

    /**
     * Template method: the overall purchasing route is fixed; subclasses customize the discount and benefits.
     * Pure calculation - call {@link #recordPurchase(PurchaseResult)} once the sale is committed.
     */
    public final PurchaseResult purchase(List<SaleLine> lines) {
        BigDecimal subtotal = money(SaleLine.sum(lines));
        BigDecimal discount = money(calculateDiscount(subtotal, lines).min(subtotal));
        return new PurchaseResult(subtotal, discount, subtotal.subtract(discount), extraBenefits(lines));
    }

    /** Updates purchase statistics after a committed sale. */
    public void recordPurchase(PurchaseResult result) {
        purchaseCount++;
        totalSpent = totalSpent.add(result.total());
    }

    /** Type-specific discount amount (not a percentage) for the given subtotal. */
    protected abstract BigDecimal calculateDiscount(BigDecimal subtotal, List<SaleLine> lines);

    /** Non-monetary perks; none by default. */
    protected List<String> extraBenefits(List<SaleLine> lines) {
        return List.of();
    }

    public abstract CustomerType getType();

    /** Human-readable summary of this type's purchasing track (shown in the customer list). */
    public abstract String getTrackDescription();

    protected static BigDecimal percentOf(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public int getPurchaseCount() { return purchaseCount; }
    public BigDecimal getTotalSpent() { return totalSpent; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPhone(String phone) { this.phone = phone; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Customer c && id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return getType() + " customer " + fullName + " (" + id + ")";
    }
}
