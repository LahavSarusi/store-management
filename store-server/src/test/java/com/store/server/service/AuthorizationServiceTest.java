package com.store.server.service;

import com.store.server.model.Role;
import org.junit.jupiter.api.Test;

import static com.store.server.TestSupport.employee;
import static com.store.server.protocol.RequestType.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {

    private final AuthorizationService authz = new AuthorizationService();

    @Test
    void anonymousCanOnlyLogin() {
        assertTrue(authz.isAllowed(null, LOGIN));
        assertFalse(authz.isAllowed(null, INVENTORY_LIST));
        assertFalse(authz.isAllowed(null, LOGOUT));
    }

    @Test
    void rolesGetTheirOwnCapabilities() {
        var cashier = employee("c", "TLV", Role.CASHIER);
        var sales = employee("s", "TLV", Role.SALESPERSON);
        var manager = employee("m", "TLV", Role.SHIFT_MANAGER);
        var admin = employee("a", null, Role.ADMIN);

        assertTrue(authz.isAllowed(cashier, SALE_CREATE));
        assertFalse(authz.isAllowed(cashier, INVENTORY_RESTOCK));
        assertFalse(authz.isAllowed(cashier, CHAT_JOIN));

        assertTrue(authz.isAllowed(sales, SALE_QUOTE));
        assertFalse(authz.isAllowed(sales, SALE_CREATE));

        assertTrue(authz.isAllowed(manager, INVENTORY_RESTOCK));
        assertTrue(authz.isAllowed(manager, CHAT_JOIN));
        assertTrue(authz.isAllowed(manager, REPORT_EXPORT));
        assertFalse(authz.isAllowed(manager, EMPLOYEE_CREATE));

        assertTrue(authz.isAllowed(admin, EMPLOYEE_CREATE));
        assertTrue(authz.isAllowed(admin, PASSWORD_POLICY_SET));
        assertTrue(authz.isAllowed(admin, LOG_LIST));
        assertTrue(authz.isAllowed(admin, CHAT_TRANSCRIPT_GET));
        assertFalse(authz.isAllowed(admin, SALE_CREATE));
        assertFalse(authz.isAllowed(manager, LOG_LIST));
    }

    @Test
    void everyLoggedInUserCanLogoutAndChangePassword() {
        for (Role r : Role.values()) {
            var e = employee("u", "TLV", r);
            assertTrue(authz.isAllowed(e, LOGOUT));
            assertTrue(authz.isAllowed(e, PASSWORD_CHANGE));
            assertTrue(authz.isAllowed(e, BRANCH_LIST));
        }
    }
}
