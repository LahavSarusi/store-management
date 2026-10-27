package com.store.server.service;

import com.store.server.TestSupport.RecordingLogger;
import com.store.server.dao.json.JsonBranchDao;
import com.store.server.dao.json.JsonConfigDao;
import com.store.server.dao.json.JsonEmployeeDao;
import com.store.server.model.Branch;
import com.store.server.model.Employee;
import com.store.server.model.PasswordPolicy;
import com.store.server.model.Role;
import com.store.server.net.ClientConnection;
import com.store.server.net.SessionManager;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.dto.Dtos.EmployeeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeServiceTest {

    @TempDir
    Path dir;

    private JsonEmployeeDao employeeDao;
    private SessionManager sessions;
    private PasswordPolicyService policy;
    private EmployeeService service;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        employeeDao = new JsonEmployeeDao(dir.resolve("employees.json"));
        JsonBranchDao branches = new JsonBranchDao(dir.resolve("branches.json"));
        branches.save(new Branch("TLV", "Tel Aviv", "Tel Aviv"));
        RecordingLogger logger = new RecordingLogger();
        policy = new PasswordPolicyService(new JsonConfigDao(dir.resolve("config.json")), logger);
        sessions = new SessionManager();
        service = new EmployeeService(employeeDao, branches, policy, sessions, logger);
        auth = new AuthService(employeeDao);
    }

    private static EmployeeRequest req(String username, String password, Role role, String branch) {
        return new EmployeeRequest(username, password, IDS.next(), "Full Name", "050-1234567", "12-600-123456", branch, role);
    }

    /** Valid, distinct Israeli ID numbers for the employees a test creates. */
    private static final java.util.Iterator<String> IDS = java.util.stream.Stream.iterate(10000000, i -> i + 1)
            .map(EmployeeServiceTest::withCheckDigit).iterator();

    private static String withCheckDigit(int eightDigits) {
        String base = String.format("%08d", eightDigits);
        for (char c = '0'; c <= '9'; c++) {
            if (com.store.server.util.Validation.isValidIsraeliId(base + c)) {
                return base + c;
            }
        }
        throw new IllegalStateException();
    }

    @Test
    void assignsSequentialEmployeeNumbers() {
        int first = service.create("admin", req("user1", "Strong123", Role.CASHIER, "TLV")).employeeNumber();
        int second = service.create("admin", req("user2", "Strong123", Role.SALESPERSON, "TLV")).employeeNumber();
        assertEquals(EmployeeService.FIRST_EMPLOYEE_NUMBER, first);
        assertEquals(first + 1, second);
    }

    @Test
    void requiresValidUniqueIdNumberAndBankAccount() {
        EmployeeRequest valid = req("user1", "Strong123", Role.CASHIER, "TLV");
        service.create("admin", valid);

        EmployeeRequest sameId = new EmployeeRequest("user2", "Strong123", valid.nationalId(), "Name",
                null, "12-600-123456", "TLV", Role.CASHIER);
        assertEquals(ErrorCode.CONFLICT, assertThrows(DomainException.class, () -> service.create("admin", sameId)).code());

        EmployeeRequest badId = new EmployeeRequest("user2", "Strong123", "123456789", "Name",
                null, "12-600-123456", "TLV", Role.CASHIER);
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(DomainException.class, () -> service.create("admin", badId)).code());

        EmployeeRequest noBank = new EmployeeRequest("user2", "Strong123", IDS.next(), "Name",
                null, null, "TLV", Role.CASHIER);
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(DomainException.class, () -> service.create("admin", noBank)).code());

        // keeping your own ID on update is not a conflict
        EmployeeRequest update = new EmployeeRequest("user1", null, valid.nationalId(), null, null, "99-1234", null, null);
        assertEquals("99-1234", service.update("admin", update).bankAccount());
    }

    @Test
    void createdEmployeeCanAuthenticate() {
        service.create("admin", req("Dana.K", "Strong123", Role.CASHIER, "TLV"));
        Employee e = auth.authenticate("dana.k", "Strong123");
        assertEquals(Role.CASHIER, e.getRole());
        assertEquals("TLV", e.getBranchId());
        assertThrows(DomainException.class, () -> auth.authenticate("dana.k", "wrong"));
        assertThrows(DomainException.class, () -> auth.authenticate("nobody", "Strong123"));
    }

    @Test
    void enforcesPasswordPolicyAndAdminCanChangeIt() {
        DomainException weak = assertThrows(DomainException.class,
                () -> service.create("admin", req("user1", "short", Role.CASHIER, "TLV")));
        assertEquals(ErrorCode.POLICY_VIOLATION, weak.code());

        policy.update("admin", new PasswordPolicy(4, false, false, false, 0));
        assertDoesNotThrow(() -> service.create("admin", req("user1", "short", Role.CASHIER, "TLV")));
    }

    @Test
    void rejectsDuplicatesAndBadBranches() {
        service.create("admin", req("user1", "Strong123", Role.CASHIER, "TLV"));
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(DomainException.class, () -> service.create("admin", req("USER1", "Strong123", Role.CASHIER, "TLV"))).code());
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(DomainException.class, () -> service.create("admin", req("user2", "Strong123", Role.CASHIER, "XXX"))).code());
        assertNull(service.create("admin", req("boss", "Strong123", Role.ADMIN, "TLV")).branchId(), "admins have no branch");
    }

    @Test
    void deletingAnOnlineEmployeeKicksThemOut() {
        service.create("admin", req("user1", "Strong123", Role.CASHIER, "TLV"));
        AtomicBoolean closed = new AtomicBoolean();
        sessions.register("user1", new ClientConnection() {
            @Override public Optional<Employee> employee() { return Optional.empty(); }
            @Override public void send(Object message) { }
            @Override public void close() { closed.set(true); }
        });

        service.delete("admin", "user1");

        assertTrue(closed.get());
        assertTrue(employeeDao.findByUsername("user1").isEmpty());
        assertThrows(DomainException.class, () -> service.delete("admin", "admin"));
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        service.create("admin", req("user1", "Strong123", Role.CASHIER, "TLV"));
        Employee e = employeeDao.findByUsername("user1").orElseThrow();
        assertThrows(DomainException.class, () -> service.changePassword(e, "wrong", "Better456"));
        service.changePassword(e, "Strong123", "Better456");
        assertNotNull(auth.authenticate("user1", "Better456"));
    }
}
