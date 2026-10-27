package com.store.server.net;

import com.store.server.model.Employee;

import java.util.Optional;

/** A connected client as seen by the rest of the server (lets services and tests avoid real sockets). */
public interface ClientConnection {

    /** The logged-in employee, empty before LOGIN or after LOGOUT. */
    Optional<Employee> employee();

    /** Sends a response or event. Thread-safe. */
    void send(Object message);

    /** Closes the underlying connection. */
    void close();
}
