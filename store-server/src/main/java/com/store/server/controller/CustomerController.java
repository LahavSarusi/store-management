package com.store.server.controller;

import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.protocol.dto.Dtos.CustomerRequest;
import com.store.server.service.CustomerService;

import static com.store.server.controller.Controller.body;

/** The global customer list. Changes are pushed to everyone as CUSTOMER_UPDATED by the service. */
public class CustomerController implements Controller {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @Override
    public void registerRoutes(RequestDispatcher d) {
        d.register(RequestType.CUSTOMER_LIST, (req, c) -> Response.ok(req.requestId(), customers.list()))
         .register(RequestType.CUSTOMER_CREATE, (req, c) -> Response.ok(req.requestId(),
                 customers.create(c.requireEmployee().getUsername(), body(req, CustomerRequest.class))))
         .register(RequestType.CUSTOMER_UPDATE, (req, c) -> Response.ok(req.requestId(),
                 customers.update(c.requireEmployee().getUsername(), body(req, CustomerRequest.class))));
    }
}
