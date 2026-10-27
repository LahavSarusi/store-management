package com.store.server.protocol.dto;

import com.store.server.logging.ActionType;
import com.store.server.model.Branch;
import com.store.server.model.Category;
import com.store.server.model.Employee;
import com.store.server.model.InventoryItem;
import com.store.server.model.Permission;
import com.store.server.model.PurchaseResult;
import com.store.server.model.Role;
import com.store.server.model.Sale;
import com.store.server.model.SaleLine;
import com.store.server.model.customer.Customer;
import com.store.server.model.customer.CustomerType;
import com.store.server.report.GroupBy;
import com.store.server.report.SalesReport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Request/response payload shapes of the wire protocol, grouped by route.
 * Field names here are exactly the JSON keys; the client project should mirror these records.
 */
public final class Dtos {

    private Dtos() { }

    // ---------------------------------------------------------------- auth
    public record LoginRequest(String username, String password) { }

    public record LoginResult(EmployeeView employee, Branch branch, Set<Permission> permissions,
                              boolean passwordExpired) { }

    public record PasswordChangeRequest(String currentPassword, String newPassword) { }

    // ---------------------------------------------------------------- admin
    /** Employee without the password hash. */
    public record EmployeeView(String username, int employeeNumber, String nationalId, String fullName,
                               String phone, String bankAccount, String branchId, Role role) {
        public static EmployeeView of(Employee e) {
            return new EmployeeView(e.getUsername(), e.getEmployeeNumber(), e.getNationalId(), e.getFullName(),
                    e.getPhone(), e.getBankAccount(), e.getBranchId(), e.getRole());
        }
    }

    /**
     * For create, everything except {@code phone} is required ({@code branchId} not for ADMIN);
     * for update, null means "unchanged". The employee number is assigned by the server.
     */
    public record EmployeeRequest(String username, String password, String nationalId, String fullName,
                                  String phone, String bankAccount, String branchId, Role role) { }

    public record UsernameRequest(String username) { }

    // ---------------------------------------------------------------- customers
    /** For update null means "unchanged"; {@code type} defaults to NEW on create. */
    public record CustomerRequest(String id, String fullName, String phone, CustomerType type) { }

    public record CustomerEvent(Customer customer) { }

    // ---------------------------------------------------------------- inventory
    /** Adds stock. {@code name/category/price} are required only for a SKU the branch doesn't carry yet. */
    public record RestockRequest(String sku, int quantity, String name, Category category, BigDecimal price) { }

    /** INVENTORY_LIST response and INVENTORY_UPDATED event payload. */
    public record InventoryView(String branchId, List<InventoryItem> items) { }

    // ---------------------------------------------------------------- sales
    public record SaleLineRequest(String sku, int quantity) { }

    public record SaleRequest(String customerId, List<SaleLineRequest> lines) { }

    public record SaleQuote(String customerId, CustomerType customerType, List<SaleLine> lines,
                            PurchaseResult result) { }

    /** {@code customer} is the state after the sale (it may have been upgraded). */
    public record SaleResult(Sale sale, List<String> benefits, Customer customer) { }

    // ---------------------------------------------------------------- reports
    /**
     * Every filter is optional; empty lists mean "all". Dates are ISO (yyyy-MM-dd): either {@code date}
     * for a daily report, or an inclusive {@code from/to} range - not both.
     */
    public record ReportRequest(GroupBy groupBy, String branchId, List<String> skus, List<Category> categories,
                                String date, String from, String to) { }

    public record ReportResult(SalesReport report, String text) { }

    public record ReportExport(String fileName, String text) { }

    // ---------------------------------------------------------------- chat
    public record ChatTargetRequest(String targetBranchId) { }

    public record ChatRequestIdRequest(String requestId) { }

    public record ChatSessionRequest(String sessionId) { }

    public record ChatSendRequest(String sessionId, String text) { }

    public record ChatLeaveRequest(String sessionId, boolean saveTranscript) { }

    // ---------------------------------------------------------------- logs
    /** Every field optional: {@code type} = one action type, ISO {@code from/to} dates, {@code limit} (default 200). */
    public record LogQueryRequest(ActionType type, String from, String to, Integer limit) { }

    public record TranscriptView(String sessionId, String text) { }
}
