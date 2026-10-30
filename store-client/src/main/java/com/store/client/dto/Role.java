package com.store.client.dto;

/** Employee roles. Permissions per role are decided by the server and sent at login. */
public enum Role {
    ADMIN, SHIFT_MANAGER, CASHIER, SALESPERSON;

    /** Every role except ADMIN works in a specific branch. */
    public boolean isBranchBound() {
        return this != ADMIN;
    }
}
