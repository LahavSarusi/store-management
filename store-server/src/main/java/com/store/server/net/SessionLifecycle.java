package com.store.server.net;

import com.store.server.model.Employee;

/** Hooks the net layer calls when a session starts or ends (presence for chat, audit log). */
public interface SessionLifecycle {

    /** Called right after the LOGIN response has been sent. */
    void onLogin(Employee employee);

    /** Called on LOGOUT or when the connection drops. */
    void onLogout(Employee employee, String reason);
}
