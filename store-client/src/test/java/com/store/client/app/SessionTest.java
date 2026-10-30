package com.store.client.app;

import com.store.client.dto.Dtos.Branch;
import com.store.client.dto.Dtos.EmployeeView;
import com.store.client.dto.Dtos.LoginResult;
import com.store.client.dto.Permission;
import com.store.client.dto.Role;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.store.client.dto.Permission.*;
import static org.junit.jupiter.api.Assertions.*;

/** The tabs each role sees, using the permission sets the server's Role enum grants. */
class SessionTest {

    private static final Set<Permission> ADMIN_PERMS =
            EnumSet.of(MANAGE_EMPLOYEES, MANAGE_PASSWORD_POLICY, VIEW_CUSTOMERS, VIEW_REPORTS, VIEW_LOGS);
    private static final Set<Permission> MANAGER_PERMS = EnumSet.of(VIEW_CUSTOMERS, EDIT_CUSTOMERS, VIEW_INVENTORY,
            RESTOCK_INVENTORY, QUOTE_SALE, SELL, VIEW_REPORTS, CHAT, SUPERVISE_CHAT);
    private static final Set<Permission> CASHIER_PERMS =
            EnumSet.of(VIEW_CUSTOMERS, EDIT_CUSTOMERS, VIEW_INVENTORY, QUOTE_SALE, SELL, CHAT);
    private static final Set<Permission> SALES_PERMS =
            EnumSet.of(VIEW_CUSTOMERS, EDIT_CUSTOMERS, VIEW_INVENTORY, QUOTE_SALE, CHAT);

    @Test
    void adminSeesAdministrationButNoBranchWork() {
        assertEquals(List.of(Screen.CUSTOMERS, Screen.REPORTS, Screen.EMPLOYEES, Screen.PASSWORD_POLICY, Screen.LOGS),
                Screen.visibleFor(ADMIN_PERMS));
    }

    @Test
    void shiftManagerSeesBranchWorkReportsAndChat() {
        assertEquals(List.of(Screen.INVENTORY, Screen.SELL, Screen.CUSTOMERS, Screen.REPORTS, Screen.CHAT),
                Screen.visibleFor(MANAGER_PERMS));
    }

    @Test
    void cashierAndSalespersonSeeTheSameTabs() {
        List<Screen> expected = List.of(Screen.INVENTORY, Screen.SELL, Screen.CUSTOMERS, Screen.CHAT);
        assertEquals(expected, Screen.visibleFor(CASHIER_PERMS));
        // A salesperson gets the Sell tab for quotes; the Complete Sale button needs SELL.
        assertEquals(expected, Screen.visibleFor(SALES_PERMS));
    }

    @Test
    void noPermissionsMeansNoTabs() {
        assertTrue(Screen.visibleFor(Set.of()).isEmpty());
    }

    @Test
    void sessionReflectsTheLoginResult() {
        Session s = new Session(login(Role.CASHIER, new Branch("TLV", "Tel Aviv - Dizengoff", "Tel Aviv"),
                CASHIER_PERMS));

        assertTrue(s.has(SELL));
        assertFalse(s.has(RESTOCK_INVENTORY));
        assertFalse(s.isAdmin());
        assertEquals("TLV", s.branchId());
        assertEquals("Dana Levi (tlv.cashier) - CASHIER - Tel Aviv - Dizengoff", s.describe());
    }

    @Test
    void adminHasNoBranch() {
        Session s = new Session(login(Role.ADMIN, null, ADMIN_PERMS));
        assertTrue(s.isAdmin());
        assertNull(s.branchId());
        assertTrue(s.describe().endsWith("All branches"));
    }

    @Test
    void permissionsUnknownToThisClientAreIgnored() {
        // Gson turns an enum name this client doesn't know into null.
        Set<Permission> fromServer = new HashSet<>(Set.of(CHAT));
        fromServer.add(null);
        Session s = new Session(login(Role.SALESPERSON, null, fromServer));
        assertEquals(Set.of(CHAT), s.permissions());
    }

    private static LoginResult login(Role role, Branch branch, Set<Permission> permissions) {
        String user = role == Role.ADMIN ? "admin" : "tlv.cashier";
        String name = role == Role.ADMIN ? "System Admin" : "Dana Levi";
        EmployeeView e = new EmployeeView(user, 1001, "314159260", name, "0501234567", "12-345-678901",
                branch == null ? null : branch.id(), role);
        return new LoginResult(e, branch, permissions, false);
    }
}
