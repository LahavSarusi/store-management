package com.store.server.service;

import com.store.server.dao.SaleDao;
import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.model.Employee;
import com.store.server.model.InventoryItem;
import com.store.server.model.PurchaseResult;
import com.store.server.model.Sale;
import com.store.server.model.SaleLine;
import com.store.server.model.customer.Customer;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.dto.Dtos.SaleLineRequest;
import com.store.server.protocol.dto.Dtos.SaleQuote;
import com.store.server.protocol.dto.Dtos.SaleRequest;
import com.store.server.protocol.dto.Dtos.SaleResult;
import com.store.server.util.IdGenerator;
import com.store.server.util.Validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Selling to customers. The price is computed by the customer's own class ({@link Customer#purchase}),
 * so each customer type follows its own purchasing route.
 */
public class SalesService {

    private final InventoryService inventory;
    private final CustomerService customers;
    private final SaleDao saleDao;
    private final ActionLogger logger;
    /** One lock per customer id: see {@link #sell}. */
    private final Map<String, ReentrantLock> customerLocks = new ConcurrentHashMap<>();

    public SalesService(InventoryService inventory, CustomerService customers, SaleDao saleDao, ActionLogger logger) {
        this.inventory = inventory;
        this.customers = customers;
        this.saleDao = saleDao;
        this.logger = logger;
    }

    /** Price preview; validates stock but changes nothing. */
    public SaleQuote quote(Employee employee, SaleRequest req) {
        Map<String, Integer> wanted = mergeLines(req);
        Customer customer = customers.get(req.customerId());
        Map<String, InventoryItem> stock = new LinkedHashMap<>();
        inventory.list(employee.getBranchId()).forEach(i -> stock.put(i.getSku(), i));
        List<SaleLine> lines = buildLines(stock, wanted);
        return new SaleQuote(customer.getId(), customer.getType(), lines, customer.purchase(lines));
    }

    /**
     * Checks stock, prices the sale and decrements stock under the branch lock, then records the sale and
     * updates the customer. If the sale cannot be recorded, the stock is put back.
     * <p>
     * Sales to the same customer run one at a time (in any branch), so the price is always computed from the
     * customer's current statistics - e.g. the first-purchase discount cannot be given twice.
     */
    public SaleResult sell(Employee employee, SaleRequest req) {
        Map<String, Integer> wanted = mergeLines(req);
        ReentrantLock customerLock = customerLocks.computeIfAbsent(req.customerId().trim(), k -> new ReentrantLock());
        customerLock.lock();
        try {
            return sellLocked(employee, req.customerId(), wanted);
        } finally {
            customerLock.unlock();
        }
    }

    private SaleResult sellLocked(Employee employee, String customerId, Map<String, Integer> wanted) {
        Customer customer = customers.get(customerId);
        String branchId = employee.getBranchId();

        record Committed(Sale sale, PurchaseResult result) { }
        Committed committed = inventory.modify(branchId, stock -> {
            List<SaleLine> lines = buildLines(stock, wanted);
            PurchaseResult result = customer.purchase(lines);
            lines.forEach(l -> {
                InventoryItem item = stock.get(l.sku());
                item.setQuantity(item.getQuantity() - l.quantity());
            });
            Sale sale = new Sale(IdGenerator.next("SALE"), branchId, employee.getUsername(),
                    customer.getId(), customer.getType(), lines,
                    result.subtotal(), result.discount(), result.total(), System.currentTimeMillis());
            return new Committed(sale, result);
        });

        // The stock is saved by now. Record the sale; if that fails, give the stock back so the two never disagree.
        try {
            saleDao.save(committed.sale());
        } catch (RuntimeException e) {
            inventory.modify(branchId, stock -> {
                committed.sale().lines().forEach(l -> {
                    InventoryItem item = stock.get(l.sku());
                    if (item != null) {
                        item.setQuantity(item.getQuantity() + l.quantity());
                    }
                });
                return null;
            });
            throw e;
        }

        Customer after = customers.recordPurchase(employee.getUsername(), customer.getId(), committed.result());
        Sale sale = committed.sale();
        logger.log(ActionType.SALE, employee.getUsername(), "sale=" + sale.id() + " branch=" + sale.branchId()
                + " customer=" + customer.getId() + " (" + customer.getType() + ")"
                + " units=" + SaleLine.totalUnits(sale.lines()) + " total=" + sale.total());
        return new SaleResult(sale, committed.result().benefits(), after);
    }

    /** Validates the request and merges repeated SKUs: sku -> total quantity, in request order. */
    private static Map<String, Integer> mergeLines(SaleRequest req) {
        Validation.require(req != null, "Missing sale");
        Validation.requireText(req.customerId(), "customerId");
        Validation.require(req.lines() != null && !req.lines().isEmpty(), "A sale needs at least one line");
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (SaleLineRequest l : req.lines()) {
            Validation.requireText(l.sku(), "sku");
            Validation.require(l.quantity() > 0, "quantity must be positive");
            wanted.merge(l.sku(), l.quantity(), Integer::sum);
        }
        return wanted;
    }

    private static List<SaleLine> buildLines(Map<String, InventoryItem> stock, Map<String, Integer> wanted) {
        List<SaleLine> lines = new ArrayList<>();
        wanted.forEach((sku, qty) -> {
            InventoryItem item = stock.get(sku);
            if (item == null) {
                throw new DomainException(ErrorCode.NOT_FOUND, "Product not carried by this branch: " + sku);
            }
            if (item.getQuantity() < qty) {
                throw new DomainException(ErrorCode.OUT_OF_STOCK,
                        item.getName() + ": requested " + qty + ", in stock " + item.getQuantity());
            }
            lines.add(new SaleLine(sku, item.getName(), item.getCategory(), item.getPrice(), qty));
        });
        return lines;
    }
}
