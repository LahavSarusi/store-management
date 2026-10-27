package com.store.server.service;

import com.store.server.dao.InventoryDao;
import com.store.server.event.EventBus;
import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.model.Employee;
import com.store.server.model.InventoryItem;
import com.store.server.protocol.EventType;
import com.store.server.protocol.ServerEvent;
import com.store.server.protocol.dto.Dtos.InventoryView;
import com.store.server.protocol.dto.Dtos.RestockRequest;
import com.store.server.util.Validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Branch-specific stock. All changes go through {@link #modify}, which serializes writers per branch
 * (different branches never block each other) and pushes INVENTORY_UPDATED to that branch afterwards.
 */
public class InventoryService {

    private final InventoryDao inventoryDao;
    private final EventBus bus;
    private final ActionLogger logger;
    private final Map<String, ReentrantLock> branchLocks = new ConcurrentHashMap<>();

    public InventoryService(InventoryDao inventoryDao, EventBus bus, ActionLogger logger) {
        this.inventoryDao = inventoryDao;
        this.bus = bus;
        this.logger = logger;
    }

    public List<InventoryItem> list(String branchId) {
        return inventoryDao.findByBranch(branchId);
    }

    /**
     * Runs {@code operation} on the branch stock (keyed by SKU) under the branch lock and persists the result.
     * If the operation throws, nothing is saved and no event is sent.
     */
    public <R> R modify(String branchId, Function<Map<String, InventoryItem>, R> operation) {
        ReentrantLock lock = branchLocks.computeIfAbsent(branchId, k -> new ReentrantLock());
        R result;
        List<InventoryItem> snapshot;
        lock.lock();
        try {
            Map<String, InventoryItem> stock = new LinkedHashMap<>();
            inventoryDao.findByBranch(branchId).forEach(i -> stock.put(i.getSku(), i));
            result = operation.apply(stock);
            snapshot = new ArrayList<>(stock.values());
            inventoryDao.saveBranch(branchId, snapshot);
        } finally {
            lock.unlock();
        }
        bus.toBranch(branchId, ServerEvent.of(EventType.INVENTORY_UPDATED, new InventoryView(branchId, snapshot)));
        return result;
    }

    /** Buying stock from a supplier: increases quantity, or adds a new product line. */
    public InventoryItem restock(Employee employee, RestockRequest req) {
        String sku = Validation.requireText(req.sku(), "sku");
        Validation.require(req.quantity() > 0, "quantity must be positive");
        Validation.require(req.price() == null || req.price().compareTo(BigDecimal.ZERO) > 0, "price must be positive");

        InventoryItem item = modify(employee.getBranchId(), stock -> {
            InventoryItem existing = stock.get(sku);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + req.quantity());
                if (req.price() != null) {
                    existing.setPrice(req.price());
                }
                return existing.copy();
            }
            Validation.require(req.name() != null && req.category() != null && req.price() != null,
                    "name, category and price are required for a new product");
            InventoryItem created = new InventoryItem(sku, req.name().trim(), req.category(), req.price(), req.quantity());
            stock.put(sku, created);
            return created.copy();
        });
        logger.log(ActionType.RESTOCK, employee.getUsername(),
                "branch=" + employee.getBranchId() + " sku=" + sku + " +" + req.quantity() + " now=" + item.getQuantity());
        return item;
    }
}
