package com.store.client.dto;

/** Every route the server accepts; mirrors {@code com.store.server.protocol.RequestType}. */
public enum RequestType {
    // Auth
    LOGIN, LOGOUT, PASSWORD_CHANGE,
    // Admin
    EMPLOYEE_CREATE, EMPLOYEE_UPDATE, EMPLOYEE_DELETE, EMPLOYEE_LIST,
    PASSWORD_POLICY_GET, PASSWORD_POLICY_SET,
    // Reference data
    BRANCH_LIST,
    // Customers (global)
    CUSTOMER_LIST, CUSTOMER_CREATE, CUSTOMER_UPDATE,
    // Inventory (branch scoped)
    INVENTORY_LIST, INVENTORY_RESTOCK,
    // Sales
    SALE_QUOTE, SALE_CREATE,
    // Reports
    REPORT_SALES, REPORT_EXPORT,
    // Chat
    CHAT_REQUEST, CHAT_ACCEPT, CHAT_DECLINE, CHAT_SEND, CHAT_JOIN, CHAT_LEAVE, CHAT_LIST_ACTIVE,
    // Logs (admin)
    LOG_LIST, CHAT_TRANSCRIPT_LIST, CHAT_TRANSCRIPT_GET
}
