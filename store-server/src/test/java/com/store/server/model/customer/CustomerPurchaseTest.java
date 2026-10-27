package com.store.server.model.customer;

import com.store.server.model.Category;
import com.store.server.model.PurchaseResult;
import com.store.server.model.SaleLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomerPurchaseTest {

    private static SaleLine line(String price, int qty) {
        return new SaleLine("SKU-" + price, "Item " + price, Category.SHIRTS, new BigDecimal(price), qty);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void newCustomerGetsWelcomeDiscountOnlyOnFirstPurchase() {
        Customer c = CustomerFactory.create(CustomerType.NEW, "1", "A", "050-1111111");
        PurchaseResult first = c.purchase(List.of(line("100.00", 2)));
        assertMoney("200.00", first.subtotal());
        assertMoney("10.00", first.discount());
        assertMoney("190.00", first.total());
        assertTrue(first.benefits().contains("Welcome gift bag"));

        c.recordPurchase(first);
        PurchaseResult second = c.purchase(List.of(line("100.00", 2)));
        assertMoney("0.00", second.discount());
        assertTrue(second.benefits().isEmpty());
    }

    @Test
    void returningCustomerGetsBiggerDiscountOnLargeBaskets() {
        Customer c = CustomerFactory.create(CustomerType.RETURNING, "1", "A", "050-1111111");
        assertMoney("20.00", c.purchase(List.of(line("200.00", 1))).discount());
        assertMoney("75.00", c.purchase(List.of(line("250.00", 2))).discount());   // 500 -> 15%
    }

    @Test
    void vipGetsCheapestItemFreeFromThreeUnitsPlusPercentage() {
        Customer c = CustomerFactory.create(CustomerType.VIP, "1", "A", "050-1111111");
        // 100 + 2x50 = 200; cheapest unit (50) free, then 20% of 150 = 30 -> discount 80
        PurchaseResult r = c.purchase(List.of(line("100.00", 1), line("50.00", 2)));
        assertMoney("80.00", r.discount());
        assertMoney("120.00", r.total());
        assertFalse(r.benefits().isEmpty());

        // only 2 units: no free item, plain 20%
        assertMoney("40.00", c.purchase(List.of(line("100.00", 2))).discount());
    }

    @Test
    void sameCartIsPricedDifferentlyPerCustomerType() {
        List<SaleLine> cart = List.of(line("100.00", 3));
        List<Customer> customers = List.of(
                CustomerFactory.create(CustomerType.NEW, "1", "A", null),
                CustomerFactory.create(CustomerType.RETURNING, "2", "B", null),
                CustomerFactory.create(CustomerType.VIP, "3", "C", null));
        List<BigDecimal> totals = customers.stream().map(c -> c.purchase(cart).total()).toList();
        assertMoney("285.00", totals.get(0));
        assertMoney("270.00", totals.get(1));
        assertMoney("160.00", totals.get(2));   // 100 free, 20% off 200
    }

    @Test
    void emptyCartCostsNothing() {
        PurchaseResult r = CustomerFactory.create(CustomerType.VIP, "1", "A", null).purchase(List.of());
        assertMoney("0", r.total());
        assertMoney("0", r.discount());
    }

    @Test
    void factoryCreatesTheRightSubclass() {
        assertInstanceOf(NewCustomer.class, CustomerFactory.create(CustomerType.NEW, "1", "A", null));
        assertInstanceOf(ReturningCustomer.class, CustomerFactory.create(CustomerType.RETURNING, "1", "A", null));
        assertInstanceOf(VipCustomer.class, CustomerFactory.create(CustomerType.VIP, "1", "A", null));
    }

    @Test
    void upgradesFollowPurchaseHistoryAndKeepStatistics() {
        Customer fresh = CustomerFactory.create(CustomerType.NEW, "1", "A", null);
        assertSame(fresh, CustomerFactory.upgradeIfEligible(fresh));

        Customer afterOne = CustomerFactory.create(CustomerType.NEW, "1", "A", null, 1, new BigDecimal("100"));
        Customer returning = CustomerFactory.upgradeIfEligible(afterOne);
        assertEquals(CustomerType.RETURNING, returning.getType());
        assertEquals(1, returning.getPurchaseCount());

        Customer bigSpender = CustomerFactory.create(CustomerType.NEW, "1", "A", null, 1, new BigDecimal("2500"));
        assertEquals(CustomerType.VIP, CustomerFactory.upgradeIfEligible(bigSpender).getType());

        Customer vip = CustomerFactory.create(CustomerType.VIP, "1", "A", null);
        assertSame(vip, CustomerFactory.upgradeIfEligible(vip));
    }
}
