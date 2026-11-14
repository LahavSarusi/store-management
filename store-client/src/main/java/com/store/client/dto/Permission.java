package com.store.client.dto;

/** Capabilities returned at login; the client shows only what these allow. */
public enum Permission {
    MANAGE_EMPLOYEES,
    MANAGE_PASSWORD_POLICY,
    VIEW_CUSTOMERS,
    EDIT_CUSTOMERS,
    VIEW_INVENTORY,
    RESTOCK_INVENTORY,
    QUOTE_SALE,
    SELL,
    VIEW_REPORTS,
    CHAT,
    SUPERVISE_CHAT,
    VIEW_LOGS
}
