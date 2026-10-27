package com.store.server;

import com.store.server.dao.BranchDao;
import com.store.server.dao.CustomerDao;
import com.store.server.dao.EmployeeDao;
import com.store.server.dao.InventoryDao;
import com.store.server.model.Branch;
import com.store.server.model.Category;
import com.store.server.model.Employee;
import com.store.server.model.InventoryItem;
import com.store.server.model.Role;
import com.store.server.model.customer.CustomerFactory;
import com.store.server.model.customer.CustomerType;
import com.store.server.service.EmployeeService;
import com.store.server.util.PasswordHasher;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Demo data for a fresh installation. Runs only when there are no employees yet.
 * <pre>
 *   admin / Admin1234                         (ADMIN)
 *   tlv.manager, tlv.cashier, tlv.sales       (branch TLV)   password: Store1234
 *   hfa.manager, hfa.cashier, hfa.sales       (branch HFA)
 *   jlm.manager, jlm.cashier, jlm.sales       (branch JLM)
 * </pre>
 */
final class DataSeeder {

    private static final Logger LOG = Logger.getLogger(DataSeeder.class.getName());
    static final String ADMIN_PASSWORD = "Admin1234";
    static final String STAFF_PASSWORD = "Store1234";

    private final EmployeeDao employees;
    private final BranchDao branches;
    private final CustomerDao customers;
    private final InventoryDao inventory;

    DataSeeder(EmployeeDao employees, BranchDao branches, CustomerDao customers, InventoryDao inventory) {
        this.employees = employees;
        this.branches = branches;
        this.customers = customers;
        this.inventory = inventory;
    }

    void seedIfEmpty() {
        if (!employees.findAll().isEmpty()) {
            return;
        }
        LOG.info("Empty database - creating demo data");
        long now = System.currentTimeMillis();
        String staffHash = PasswordHasher.hash(STAFF_PASSWORD);

        // Valid, distinct Israeli ID numbers, one per seeded employee.
        Iterator<String> ids = List.of("300000015", "300000023", "300000031", "300000049", "300000056",
                "300000064", "300000072", "300000080", "300000098", "300000106").iterator();
        int number = EmployeeService.FIRST_EMPLOYEE_NUMBER;

        employees.save(new Employee("admin", PasswordHasher.hash(ADMIN_PASSWORD), number++, ids.next(),
                "System Administrator", null, "12-600-100100", null, Role.ADMIN, now));

        List<Branch> all = List.of(
                new Branch("TLV", "Tel Aviv - Dizengoff", "Tel Aviv"),
                new Branch("HFA", "Haifa - Grand Canyon", "Haifa"),
                new Branch("JLM", "Jerusalem - Malha", "Jerusalem"));
        for (Branch b : all) {
            branches.save(b);
            String p = b.id().toLowerCase();
            employees.save(new Employee(p + ".manager", staffHash, number, ids.next(), b.city() + " Shift Manager",
                    "050-1234567", "12-600-" + (200000 + number++), b.id(), Role.SHIFT_MANAGER, now));
            employees.save(new Employee(p + ".cashier", staffHash, number, ids.next(), b.city() + " Cashier",
                    "052-1234567", "10-800-" + (300000 + number++), b.id(), Role.CASHIER, now));
            employees.save(new Employee(p + ".sales", staffHash, number, ids.next(), b.city() + " Salesperson",
                    "054-1234567", "20-500-" + (400000 + number++), b.id(), Role.SALESPERSON, now));
            inventory.saveBranch(b.id(), demoStock());
        }

        customers.save(CustomerFactory.create(CustomerType.NEW, "314159260", "Noa Levi", "050-7654321"));
        customers.save(CustomerFactory.create(CustomerType.RETURNING, "271828188", "Avi Cohen", "052-7654321",
                3, new BigDecimal("640.00")));
        customers.save(CustomerFactory.create(CustomerType.VIP, "161803390", "Dana Mizrahi", "054-7654321",
                14, new BigDecimal("5230.00")));
    }

    private static List<InventoryItem> demoStock() {
        return List.of(
                item("SH-001", "Cotton T-Shirt", Category.SHIRTS, "59.90", 40),
                item("SH-002", "Oxford Button-Down Shirt", Category.SHIRTS, "149.90", 20),
                item("PA-001", "Slim Fit Jeans", Category.PANTS, "199.90", 25),
                item("PA-002", "Chino Pants", Category.PANTS, "169.90", 15),
                item("DR-001", "Summer Midi Dress", Category.DRESSES, "229.90", 12),
                item("OW-001", "Light Bomber Jacket", Category.OUTERWEAR, "349.90", 8),
                item("SO-001", "Canvas Sneakers", Category.SHOES, "179.90", 18),
                item("AC-001", "Leather Belt", Category.ACCESSORIES, "89.90", 30));
    }

    private static InventoryItem item(String sku, String name, Category category, String price, int qty) {
        return new InventoryItem(sku, name, category, new BigDecimal(price), qty);
    }
}
