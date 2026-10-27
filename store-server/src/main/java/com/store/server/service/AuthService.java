package com.store.server.service;

import com.store.server.dao.EmployeeDao;
import com.store.server.model.Employee;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.util.PasswordHasher;

import java.util.Locale;
import java.util.Optional;

/** Credential validation. Session bookkeeping (single login) lives in the net layer. */
public class AuthService {

    /** Verified against when the user doesn't exist, so response time doesn't reveal valid usernames. */
    private static final String DUMMY_HASH = PasswordHasher.hash("dummy-password");

    private final EmployeeDao employeeDao;

    public AuthService(EmployeeDao employeeDao) {
        this.employeeDao = employeeDao;
    }

    /** @throws DomainException INVALID_CREDENTIALS for an unknown user or wrong password (same message) */
    public Employee authenticate(String username, String password) {
        if (username == null || password == null) {
            throw new DomainException(ErrorCode.INVALID_CREDENTIALS, "Wrong username or password");
        }
        Optional<Employee> found = employeeDao.findByUsername(username.trim().toLowerCase(Locale.ROOT));
        String hash = found.map(Employee::getPasswordHash).orElse(DUMMY_HASH);
        boolean ok = PasswordHasher.verify(password, hash);
        if (found.isEmpty() || !ok) {
            throw new DomainException(ErrorCode.INVALID_CREDENTIALS, "Wrong username or password");
        }
        return found.get();
    }
}
