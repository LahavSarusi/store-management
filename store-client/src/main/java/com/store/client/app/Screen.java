package com.store.client.app;

import com.store.client.dto.Permission;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** The tabs of the main window, in display order, and the permission each one needs. */
public enum Screen {
    INVENTORY("Inventory", Permission.VIEW_INVENTORY),
    SELL("Sell", Permission.QUOTE_SALE),
    CUSTOMERS("Customers", Permission.VIEW_CUSTOMERS),
    REPORTS("Reports", Permission.VIEW_REPORTS),
    CHAT("Chat", Permission.CHAT),
    EMPLOYEES("Employees", Permission.MANAGE_EMPLOYEES),
    PASSWORD_POLICY("Password Policy", Permission.MANAGE_PASSWORD_POLICY),
    LOGS("Logs", Permission.VIEW_LOGS);

    private final String title;
    private final Permission required;

    Screen(String title, Permission required) {
        this.title = title;
        this.required = required;
    }

    public String title() {
        return title;
    }

    public Permission required() {
        return required;
    }

    /** The screens a user with these permissions may open. */
    public static List<Screen> visibleFor(Set<Permission> permissions) {
        return Arrays.stream(values()).filter(s -> permissions.contains(s.required)).toList();
    }
}
