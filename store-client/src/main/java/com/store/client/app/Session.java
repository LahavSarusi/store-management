package com.store.client.app;

import com.store.client.dto.Dtos.Branch;
import com.store.client.dto.Dtos.EmployeeView;
import com.store.client.dto.Dtos.LoginResult;
import com.store.client.dto.Permission;
import com.store.client.dto.Role;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Who is logged in on this connection, from the LOGIN response. The UI uses it only to decide what to show;
 * the server enforces permissions and branch scoping on every request anyway.
 */
public final class Session {

    private final EmployeeView employee;
    private final Branch branch;
    private final Set<Permission> permissions;

    public Session(LoginResult login) {
        this.employee = login.employee();
        this.branch = login.branch();
        Set<Permission> perms = EnumSet.noneOf(Permission.class);
        if (login.permissions() != null) {
            login.permissions().stream().filter(p -> p != null).forEach(perms::add);
        }
        this.permissions = Collections.unmodifiableSet(perms);
    }

    public EmployeeView employee() {
        return employee;
    }

    public String username() {
        return employee.username();
    }

    /** The user's branch, or null for the admin. */
    public Branch branch() {
        return branch;
    }

    public String branchId() {
        return branch == null ? null : branch.id();
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    public boolean isAdmin() {
        return employee.role() == Role.ADMIN;
    }

    public List<Screen> screens() {
        return Screen.visibleFor(permissions);
    }

    /** "Dana Levi (tlv.cashier) - CASHIER - Tel Aviv - Dizengoff" */
    public String describe() {
        String where = branch == null ? "All branches" : branch.name();
        return employee.fullName() + " (" + employee.username() + ") - " + employee.role() + " - " + where;
    }
}
