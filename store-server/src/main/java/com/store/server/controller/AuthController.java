package com.store.server.controller;

import com.store.server.dao.BranchDao;
import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.model.Branch;
import com.store.server.model.Employee;
import com.store.server.net.ClientHandler;
import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.Request;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.protocol.dto.Dtos.EmployeeView;
import com.store.server.protocol.dto.Dtos.LoginRequest;
import com.store.server.protocol.dto.Dtos.LoginResult;
import com.store.server.protocol.dto.Dtos.PasswordChangeRequest;
import com.store.server.service.AuthService;
import com.store.server.service.EmployeeService;
import com.store.server.service.PasswordPolicyService;

import static com.store.server.controller.Controller.body;

/** LOGIN, LOGOUT, PASSWORD_CHANGE, BRANCH_LIST. */
public class AuthController implements Controller {

    private final AuthService auth;
    private final EmployeeService employees;
    private final PasswordPolicyService policy;
    private final BranchDao branches;
    private final ActionLogger logger;

    public AuthController(AuthService auth, EmployeeService employees, PasswordPolicyService policy,
                          BranchDao branches, ActionLogger logger) {
        this.auth = auth;
        this.employees = employees;
        this.policy = policy;
        this.branches = branches;
        this.logger = logger;
    }

    @Override
    public void registerRoutes(RequestDispatcher d) {
        d.register(RequestType.LOGIN, this::login)
         .register(RequestType.LOGOUT, this::logout)
         .register(RequestType.PASSWORD_CHANGE, this::changePassword)
         .register(RequestType.BRANCH_LIST, (req, client) -> Response.ok(req.requestId(), branches.findAll()));
    }

    private Response login(Request req, ClientHandler client) {
        if (client.employee().isPresent()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Already logged in on this connection");
        }
        LoginRequest body = body(req, LoginRequest.class);
        Employee e;
        try {
            e = auth.authenticate(body.username(), body.password());
        } catch (DomainException ex) {
            logger.log(ActionType.LOGIN_REJECTED, body.username(), "bad credentials");
            throw ex;
        }
        if (!client.login(e)) {
            logger.log(ActionType.LOGIN_REJECTED, e.getUsername(), "already logged in elsewhere");
            throw new DomainException(ErrorCode.ALREADY_LOGGED_IN,
                    "This user is already logged in on another computer");
        }
        logger.log(ActionType.LOGIN, e.getUsername(), e.getRole() + "@" + e.getBranchId());
        Branch branch = e.getBranchId() == null ? null : branches.findById(e.getBranchId()).orElse(null);
        return Response.ok(req.requestId(), new LoginResult(EmployeeView.of(e), branch,
                e.getRole().permissions(), policy.isExpired(e)));
    }

    private Response logout(Request req, ClientHandler client) {
        client.logout();
        return Response.ok(req.requestId());
    }

    private Response changePassword(Request req, ClientHandler client) {
        PasswordChangeRequest body = body(req, PasswordChangeRequest.class);
        employees.changePassword(client.requireEmployee(), body.currentPassword(), body.newPassword());
        return Response.ok(req.requestId());
    }
}
