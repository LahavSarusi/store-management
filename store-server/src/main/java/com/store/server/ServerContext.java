package com.store.server;

import com.store.server.chat.ChatQueueManager;
import com.store.server.controller.AdminController;
import com.store.server.controller.AuthController;
import com.store.server.controller.ChatController;
import com.store.server.controller.Controller;
import com.store.server.controller.CustomerController;
import com.store.server.controller.InventoryController;
import com.store.server.controller.LogController;
import com.store.server.controller.ReportController;
import com.store.server.controller.SalesController;
import com.store.server.dao.BranchDao;
import com.store.server.dao.CustomerDao;
import com.store.server.dao.EmployeeDao;
import com.store.server.dao.InventoryDao;
import com.store.server.dao.SaleDao;
import com.store.server.dao.json.JsonBranchDao;
import com.store.server.dao.json.JsonConfigDao;
import com.store.server.dao.json.JsonCustomerDao;
import com.store.server.dao.json.JsonEmployeeDao;
import com.store.server.dao.json.JsonInventoryDao;
import com.store.server.dao.json.JsonPendingChatDao;
import com.store.server.dao.json.JsonSaleDao;
import com.store.server.event.EventBus;
import com.store.server.event.SessionEventBus;
import com.store.server.logging.ActionType;
import com.store.server.logging.FileActionLogger;
import com.store.server.model.Employee;
import com.store.server.net.RequestDispatcher;
import com.store.server.net.SessionLifecycle;
import com.store.server.net.SessionManager;
import com.store.server.net.StoreServer;
import com.store.server.report.ReportService;
import com.store.server.report.TxtReportExporter;
import com.store.server.service.AuthService;
import com.store.server.service.AuthorizationService;
import com.store.server.service.CustomerService;
import com.store.server.service.EmployeeService;
import com.store.server.service.InventoryService;
import com.store.server.service.PasswordPolicyService;
import com.store.server.service.SalesService;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

/**
 * Composition root: builds and wires every component once (manual dependency injection).
 * Using one context object instead of static singletons keeps every class testable in isolation.
 *
 * <p>Directory layout under {@code baseDir}: {@code data/} (JSON files), {@code logs/}, {@code reports/}.
 */
public final class ServerContext implements SessionLifecycle, AutoCloseable {

    private final EmployeeDao employeeDao;
    private final BranchDao branchDao;
    private final CustomerDao customerDao;
    private final InventoryDao inventoryDao;
    private final SaleDao saleDao;

    private final FileActionLogger logger;
    private final SessionManager sessions = new SessionManager();
    private final EventBus bus = new SessionEventBus(sessions);
    private final ChatQueueManager chat;
    private final RequestDispatcher dispatcher = new RequestDispatcher(new AuthorizationService());

    public ServerContext(Path baseDir) {
        Path data = baseDir.resolve("data");
        employeeDao = new JsonEmployeeDao(data.resolve("employees.json"));
        branchDao = new JsonBranchDao(data.resolve("branches.json"));
        customerDao = new JsonCustomerDao(data.resolve("customers.json"));
        inventoryDao = new JsonInventoryDao(data.resolve("inventory.json"));
        saleDao = new JsonSaleDao(data.resolve("sales.json"));
        JsonConfigDao configDao = new JsonConfigDao(data.resolve("config.json"));

        logger = new FileActionLogger(baseDir.resolve("logs"));
        chat = new ChatQueueManager(bus, new JsonPendingChatDao(data.resolve("pending_chats.json")), logger);

        PasswordPolicyService policy = new PasswordPolicyService(configDao, logger);
        EmployeeService employees = new EmployeeService(employeeDao, branchDao, policy, sessions, logger);
        CustomerService customers = new CustomerService(customerDao, bus, logger);
        InventoryService inventory = new InventoryService(inventoryDao, bus, logger);
        SalesService sales = new SalesService(inventory, customers, saleDao, logger);
        ReportService reports = new ReportService(saleDao, branchDao, ZoneId.systemDefault());

        List<Controller> controllers = List.of(
                new AuthController(new AuthService(employeeDao), employees, policy, branchDao, logger),
                new AdminController(employees, policy),
                new CustomerController(customers),
                new InventoryController(inventory),
                new SalesController(sales),
                new ReportController(reports, new TxtReportExporter(baseDir.resolve("reports")), logger),
                new ChatController(chat, branchDao),
                new LogController(logger));
        controllers.forEach(c -> c.registerRoutes(dispatcher));
    }

    /** Fills empty data files with demo branches, staff, stock and customers. */
    public void seedIfEmpty() {
        new DataSeeder(employeeDao, branchDao, customerDao, inventoryDao).seedIfEmpty();
    }

    public StoreServer createServer(int port, int maxClients) {
        return new StoreServer(port, maxClients, dispatcher, sessions, this);
    }

    // ---------------------------------------------------------------- SessionLifecycle

    @Override
    public void onLogin(Employee employee) {
        chat.onLogin(employee);
    }

    @Override
    public void onLogout(Employee employee, String reason) {
        chat.onDisconnect(employee);
        logger.log(ActionType.LOGOUT, employee.getUsername(), reason);
    }

    public SessionManager sessions() {
        return sessions;
    }

    public ChatQueueManager chat() {
        return chat;
    }

    @Override
    public void close() {
        logger.close();
    }
}
