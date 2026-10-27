package com.store.server.service;

import com.store.server.TestSupport.RecordingEventBus;
import com.store.server.TestSupport.RecordingLogger;
import com.store.server.dao.json.JsonCustomerDao;
import com.store.server.dao.json.JsonInventoryDao;
import com.store.server.dao.json.JsonSaleDao;
import com.store.server.logging.ActionType;
import com.store.server.model.Category;
import com.store.server.model.Employee;
import com.store.server.model.InventoryItem;
import com.store.server.model.Role;
import com.store.server.model.customer.CustomerFactory;
import com.store.server.model.customer.CustomerType;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.EventType;
import com.store.server.protocol.dto.Dtos.RestockRequest;
import com.store.server.protocol.dto.Dtos.SaleLineRequest;
import com.store.server.protocol.dto.Dtos.SaleQuote;
import com.store.server.protocol.dto.Dtos.SaleRequest;
import com.store.server.protocol.dto.Dtos.SaleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.store.server.TestSupport.employee;
import static org.junit.jupiter.api.Assertions.*;

/** Sales + inventory + customers working together on real JSON DAOs in a temp folder. */
class SalesServiceTest {

    @TempDir
    Path dir;

    private final Employee cashier = employee("tlv.cashier", "TLV", Role.CASHIER);
    private RecordingEventBus bus;
    private RecordingLogger logger;
    private JsonInventoryDao inventoryDao;
    private JsonSaleDao saleDao;
    private InventoryService inventory;
    private CustomerService customers;
    private SalesService sales;

    @BeforeEach
    void setUp() {
        bus = new RecordingEventBus();
        logger = new RecordingLogger();
        inventoryDao = new JsonInventoryDao(dir.resolve("inventory.json"));
        inventoryDao.saveBranch("TLV", List.of(
                new InventoryItem("SH-001", "T-Shirt", Category.SHIRTS, new BigDecimal("50.00"), 10),
                new InventoryItem("PA-001", "Jeans", Category.PANTS, new BigDecimal("200.00"), 2)));
        JsonCustomerDao customerDao = new JsonCustomerDao(dir.resolve("customers.json"));
        customerDao.save(CustomerFactory.create(CustomerType.NEW, "314159260", "Noa", "050-1111111"));
        saleDao = new JsonSaleDao(dir.resolve("sales.json"));

        inventory = new InventoryService(inventoryDao, bus, logger);
        customers = new CustomerService(customerDao, bus, logger);
        sales = new SalesService(inventory, customers, saleDao, logger);
    }

    private static SaleRequest sale(String customerId, String sku, int qty) {
        return new SaleRequest(customerId, List.of(new SaleLineRequest(sku, qty)));
    }

    private int stockOf(String sku) {
        return inventory.list("TLV").stream().filter(i -> i.getSku().equals(sku)).findFirst().orElseThrow().getQuantity();
    }

    @Test
    void sellAppliesCustomerLogicDecrementsStockAndSyncsLive() {
        SaleResult r = sales.sell(cashier, sale("314159260", "SH-001", 2));

        assertEquals(0, new BigDecimal("95.00").compareTo(r.sale().total()), "NEW customer first purchase: 5% off 100");
        assertEquals(8, stockOf("SH-001"));
        assertEquals(1, saleDao.findAll().size());
        assertEquals(CustomerType.RETURNING, r.customer().getType(), "upgraded after first purchase");
        assertEquals(1, bus.count("branch", EventType.INVENTORY_UPDATED));
        assertTrue(bus.count("all", EventType.CUSTOMER_UPDATED) >= 1);
        assertTrue(logger.has(ActionType.SALE));
        assertTrue(logger.has(ActionType.CUSTOMER_UPGRADED));
    }

    @Test
    void outOfStockChangesNothing() {
        DomainException e = assertThrows(DomainException.class, () -> sales.sell(cashier, sale("314159260", "PA-001", 3)));
        assertEquals(ErrorCode.OUT_OF_STOCK, e.code());
        assertEquals(2, stockOf("PA-001"));
        assertTrue(saleDao.findAll().isEmpty());
        assertEquals(0, bus.count("branch", EventType.INVENTORY_UPDATED));
    }

    @Test
    void repeatedSkusAreMergedBeforeTheStockCheck() {
        SaleRequest req = new SaleRequest("314159260",
                List.of(new SaleLineRequest("PA-001", 2), new SaleLineRequest("PA-001", 1)));
        assertThrows(DomainException.class, () -> sales.sell(cashier, req));
    }

    @Test
    void unknownCustomerOrProductIsNotFound() {
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(DomainException.class, () -> sales.sell(cashier, sale("000000018", "SH-001", 1))).code());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(DomainException.class, () -> sales.sell(cashier, sale("314159260", "XX-999", 1))).code());
    }

    @Test
    void quoteDoesNotChangeAnything() {
        SaleQuote q = sales.quote(cashier, sale("314159260", "SH-001", 4));
        assertEquals(0, new BigDecimal("190.00").compareTo(q.result().total()));
        assertEquals(10, stockOf("SH-001"));
        assertTrue(saleDao.findAll().isEmpty());
    }

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        int buyers = 25;   // stock is 10
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger sold = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < buyers; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    sales.sell(cashier, sale("314159260", "SH-001", 1));
                    sold.incrementAndGet();
                } catch (DomainException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(10, sold.get());
        assertEquals(15, rejected.get());
        assertEquals(0, stockOf("SH-001"));
        assertEquals(10, saleDao.findAll().size());
    }

    @Test
    void restockAddsToExistingOrCreatesNewProduct() {
        Employee manager = employee("tlv.manager", "TLV", Role.SHIFT_MANAGER);
        inventory.restock(manager, new RestockRequest("SH-001", 5, null, null, null));
        assertEquals(15, stockOf("SH-001"));

        inventory.restock(manager, new RestockRequest("AC-009", 3, "Scarf", Category.ACCESSORIES, new BigDecimal("39.90")));
        assertEquals(3, stockOf("AC-009"));

        assertThrows(DomainException.class,
                () -> inventory.restock(manager, new RestockRequest("NEW-1", 3, null, null, null)));
        assertTrue(logger.has(ActionType.RESTOCK));
    }
}
