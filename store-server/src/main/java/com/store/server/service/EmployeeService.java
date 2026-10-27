package com.store.server.service;

import com.store.server.dao.BranchDao;
import com.store.server.dao.EmployeeDao;
import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.model.Employee;
import com.store.server.model.Role;
import com.store.server.net.SessionManager;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.dto.Dtos.EmployeeRequest;
import com.store.server.protocol.dto.Dtos.EmployeeView;
import com.store.server.util.PasswordHasher;
import com.store.server.util.Validation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Admin management of employee accounts, plus self-service password change. */
public class EmployeeService {

    public static final int FIRST_EMPLOYEE_NUMBER = 1001;

    private final EmployeeDao employeeDao;
    private final BranchDao branchDao;
    private final PasswordPolicyService policy;
    private final SessionManager sessions;
    private final ActionLogger logger;

    public EmployeeService(EmployeeDao employeeDao, BranchDao branchDao, PasswordPolicyService policy,
                           SessionManager sessions, ActionLogger logger) {
        this.employeeDao = employeeDao;
        this.branchDao = branchDao;
        this.policy = policy;
        this.sessions = sessions;
        this.logger = logger;
    }

    public List<EmployeeView> list() {
        return employeeDao.findAll().stream()
                .sorted(Comparator.comparing(Employee::getBranchId, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Employee::getUsername))
                .map(EmployeeView::of)
                .toList();
    }

    public synchronized EmployeeView create(String actor, EmployeeRequest req) {
        String username = normalize(req.username());
        Validation.require(Validation.isValidUsername(username), "Username must be 3-30 letters, digits, '.' or '_'");
        if (employeeDao.findByUsername(username).isPresent()) {
            throw new DomainException(ErrorCode.CONFLICT, "Username already exists");
        }
        Validation.require(req.role() != null, "role is required");
        String nationalId = validateNationalId(req.nationalId(), null);
        String fullName = Validation.requireText(req.fullName(), "fullName");
        validatePhone(req.phone());
        String bankAccount = validateBankAccount(req.bankAccount());
        String branchId = resolveBranch(req.role(), req.branchId());
        policy.validate(req.password());

        Employee e = new Employee(username, PasswordHasher.hash(req.password()), nextEmployeeNumber(),
                nationalId, fullName, req.phone(), bankAccount, branchId, req.role(), System.currentTimeMillis());
        employeeDao.save(e);
        logger.log(ActionType.EMPLOYEE_CREATED, actor, e.toString());
        return EmployeeView.of(e);
    }

    /** Null fields are left unchanged. Changing role or branch logs the user out so permissions refresh. */
    public synchronized EmployeeView update(String actor, EmployeeRequest req) {
        Employee e = require(normalize(req.username()));
        boolean accessChanged = false;

        if (req.fullName() != null) {
            e.setFullName(Validation.requireText(req.fullName(), "fullName"));
        }
        if (req.phone() != null) {
            validatePhone(req.phone());
            e.setPhone(req.phone());
        }
        if (req.nationalId() != null) {
            e.setNationalId(validateNationalId(req.nationalId(), e.getUsername()));
        }
        if (req.bankAccount() != null) {
            e.setBankAccount(validateBankAccount(req.bankAccount()));
        }
        if (req.role() != null || req.branchId() != null) {
            Role role = req.role() != null ? req.role() : e.getRole();
            String branchId = resolveBranch(role, req.branchId() != null ? req.branchId() : e.getBranchId());
            accessChanged = role != e.getRole() || !Objects.equals(branchId, e.getBranchId());
            if (accessChanged && e.getUsername().equals(actor) && role != Role.ADMIN) {
                throw new DomainException(ErrorCode.CONFLICT, "You cannot remove your own admin role");
            }
            e.setRole(role);
            e.setBranchId(branchId);
        }
        if (req.password() != null) {
            policy.validate(req.password());
            e.setPasswordHash(PasswordHasher.hash(req.password()));
            e.setPasswordChangedAt(System.currentTimeMillis());
        }
        employeeDao.save(e);
        logger.log(ActionType.EMPLOYEE_UPDATED, actor, e.toString());
        if (accessChanged) {
            sessions.forceLogout(e.getUsername(), "Your account was changed by an administrator. Please log in again.");
        }
        return EmployeeView.of(e);
    }

    public synchronized void delete(String actor, String username) {
        String key = normalize(username);
        if (key.equals(actor)) {
            throw new DomainException(ErrorCode.CONFLICT, "You cannot delete your own account");
        }
        Employee e = require(key);
        employeeDao.delete(key);
        logger.log(ActionType.EMPLOYEE_DELETED, actor, e.toString());
        sessions.forceLogout(key, "Your account was deleted.");
    }

    public synchronized void changePassword(Employee self, String currentPassword, String newPassword) {
        Employee e = require(self.getUsername());
        if (!PasswordHasher.verify(currentPassword, e.getPasswordHash())) {
            throw new DomainException(ErrorCode.INVALID_CREDENTIALS, "Current password is wrong");
        }
        Validation.require(!Objects.equals(currentPassword, newPassword), "New password must differ from the current one");
        policy.validate(newPassword);
        e.setPasswordHash(PasswordHasher.hash(newPassword));
        e.setPasswordChangedAt(System.currentTimeMillis());
        employeeDao.save(e);
        logger.log(ActionType.PASSWORD_CHANGED, e.getUsername(), "");
    }

    private Employee require(String username) {
        return employeeDao.findByUsername(username)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Employee not found: " + username));
    }

    private String resolveBranch(Role role, String branchId) {
        if (!role.isBranchBound()) {
            return null;
        }
        Validation.require(branchId != null && branchDao.findById(branchId).isPresent(),
                "A valid branchId is required for role " + role);
        return branchId;
    }

    /** Valid Israeli ID that no other employee has. {@code owner} is the employee being updated, if any. */
    private String validateNationalId(String nationalId, String owner) {
        String id = Validation.requireText(nationalId, "nationalId");
        Validation.require(Validation.isValidIsraeliId(id), "Invalid ID number");
        boolean taken = employeeDao.findAll().stream()
                .anyMatch(other -> id.equals(other.getNationalId()) && !other.getUsername().equals(owner));
        if (taken) {
            throw new DomainException(ErrorCode.CONFLICT, "Another employee already has this ID number");
        }
        return id;
    }

    private static String validateBankAccount(String bankAccount) {
        String account = Validation.requireText(bankAccount, "bankAccount");
        Validation.require(Validation.isValidBankAccount(account), "Bank account must be 4-20 digits or dashes");
        return account;
    }

    /** Sequential employee numbers starting at {@value #FIRST_EMPLOYEE_NUMBER}. Caller holds the service lock. */
    private int nextEmployeeNumber() {
        return employeeDao.findAll().stream()
                .mapToInt(Employee::getEmployeeNumber)
                .max()
                .orElse(FIRST_EMPLOYEE_NUMBER - 1) + 1;
    }

    private static void validatePhone(String phone) {
        Validation.require(phone == null || Validation.isValidPhone(phone), "Invalid phone number");
    }

    private static String normalize(String username) {
        return Validation.requireText(username, "username").toLowerCase(Locale.ROOT);
    }
}
