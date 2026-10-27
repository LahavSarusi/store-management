package com.store.server.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.store.server.model.Permission.*;

/** Employee roles and the permissions each one carries. */
public enum Role {
    /** Network administrator: manages accounts and policy, not tied to a branch. */
    ADMIN(EnumSet.of(MANAGE_EMPLOYEES, MANAGE_PASSWORD_POLICY, VIEW_CUSTOMERS, VIEW_REPORTS, VIEW_LOGS)),
    SHIFT_MANAGER(EnumSet.of(VIEW_CUSTOMERS, EDIT_CUSTOMERS, VIEW_INVENTORY, RESTOCK_INVENTORY,
            QUOTE_SALE, SELL, VIEW_REPORTS, CHAT, SUPERVISE_CHAT)),
    CASHIER(EnumSet.of(VIEW_CUSTOMERS, EDIT_CUSTOMERS, VIEW_INVENTORY, QUOTE_SALE, SELL, CHAT)),
    SALESPERSON(EnumSet.of(VIEW_CUSTOMERS, EDIT_CUSTOMERS, VIEW_INVENTORY, QUOTE_SALE, CHAT));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /** Every role except ADMIN works in a specific branch. */
    public boolean isBranchBound() {
        return this != ADMIN;
    }
}
