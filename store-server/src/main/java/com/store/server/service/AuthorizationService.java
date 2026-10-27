package com.store.server.service;

import com.store.server.model.Employee;
import com.store.server.model.Permission;
import com.store.server.protocol.RequestType;

import java.util.EnumMap;
import java.util.Map;

import static com.store.server.protocol.RequestType.*;

/**
 * Role-based access control: maps each route to the permission it needs.
 * Routes absent from the map only require being logged in. Branch scoping is enforced by the services,
 * which always take the branch from the session rather than from the payload.
 */
public class AuthorizationService {

    private static final Map<RequestType, Permission> REQUIRED = new EnumMap<>(RequestType.class);

    static {
        require(Permission.MANAGE_EMPLOYEES, EMPLOYEE_CREATE, EMPLOYEE_UPDATE, EMPLOYEE_DELETE, EMPLOYEE_LIST);
        require(Permission.MANAGE_PASSWORD_POLICY, PASSWORD_POLICY_GET, PASSWORD_POLICY_SET);
        require(Permission.VIEW_CUSTOMERS, CUSTOMER_LIST);
        require(Permission.EDIT_CUSTOMERS, CUSTOMER_CREATE, CUSTOMER_UPDATE);
        require(Permission.VIEW_INVENTORY, INVENTORY_LIST);
        require(Permission.RESTOCK_INVENTORY, INVENTORY_RESTOCK);
        require(Permission.QUOTE_SALE, SALE_QUOTE);
        require(Permission.SELL, SALE_CREATE);
        require(Permission.VIEW_REPORTS, REPORT_SALES, REPORT_EXPORT);
        require(Permission.CHAT, CHAT_REQUEST, CHAT_ACCEPT, CHAT_DECLINE, CHAT_SEND, CHAT_LEAVE);
        require(Permission.SUPERVISE_CHAT, CHAT_JOIN, CHAT_LIST_ACTIVE);
        require(Permission.VIEW_LOGS, LOG_LIST, CHAT_TRANSCRIPT_LIST, CHAT_TRANSCRIPT_GET);
    }

    private static void require(Permission permission, RequestType... types) {
        for (RequestType t : types) {
            REQUIRED.put(t, permission);
        }
    }

    /** @param employee the logged-in employee, or null when not logged in */
    public boolean isAllowed(Employee employee, RequestType type) {
        if (type == LOGIN) {
            return true;
        }
        if (employee == null) {
            return false;
        }
        Permission needed = REQUIRED.get(type);
        return needed == null || employee.getRole().has(needed);
    }
}
