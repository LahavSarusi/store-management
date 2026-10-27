package com.store.server.controller;

import com.store.server.model.PasswordPolicy;
import com.store.server.net.ClientHandler;
import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.Request;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.protocol.dto.Dtos.EmployeeRequest;
import com.store.server.protocol.dto.Dtos.UsernameRequest;
import com.store.server.service.EmployeeService;
import com.store.server.service.PasswordPolicyService;

import static com.store.server.controller.Controller.body;

/** Employee accounts and password policy (ADMIN only, enforced by the dispatcher). */
public class AdminController implements Controller {

    private final EmployeeService employees;
    private final PasswordPolicyService policy;

    public AdminController(EmployeeService employees, PasswordPolicyService policy) {
        this.employees = employees;
        this.policy = policy;
    }

    @Override
    public void registerRoutes(RequestDispatcher d) {
        d.register(RequestType.EMPLOYEE_LIST, (req, c) -> Response.ok(req.requestId(), employees.list()))
         .register(RequestType.EMPLOYEE_CREATE, (req, c) ->
                 Response.ok(req.requestId(), employees.create(actor(c), body(req, EmployeeRequest.class))))
         .register(RequestType.EMPLOYEE_UPDATE, (req, c) ->
                 Response.ok(req.requestId(), employees.update(actor(c), body(req, EmployeeRequest.class))))
         .register(RequestType.EMPLOYEE_DELETE, this::delete)
         .register(RequestType.PASSWORD_POLICY_GET, (req, c) -> Response.ok(req.requestId(), policy.current()))
         .register(RequestType.PASSWORD_POLICY_SET, (req, c) ->
                 Response.ok(req.requestId(), policy.update(actor(c), body(req, PasswordPolicy.class))));
    }

    private Response delete(Request req, ClientHandler c) {
        employees.delete(actor(c), body(req, UsernameRequest.class).username());
        return Response.ok(req.requestId());
    }

    private static String actor(ClientHandler c) {
        return c.requireEmployee().getUsername();
    }
}
