package com.store.server.service;

import com.store.server.dao.ConfigDao;
import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.model.Employee;
import com.store.server.model.PasswordPolicy;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.util.Validation;

import java.util.List;

/** Reads, updates and enforces the admin-defined password policy. */
public class PasswordPolicyService {

    private final ConfigDao configDao;
    private final ActionLogger logger;

    public PasswordPolicyService(ConfigDao configDao, ActionLogger logger) {
        this.configDao = configDao;
        this.logger = logger;
    }

    public PasswordPolicy current() {
        return configDao.loadPasswordPolicy();
    }

    public PasswordPolicy update(String actor, PasswordPolicy policy) {
        Validation.require(policy != null, "Policy is required");
        Validation.require(policy.minLength() >= 4 && policy.minLength() <= 64, "minLength must be 4-64");
        Validation.require(policy.expiryDays() >= 0, "expiryDays cannot be negative");
        configDao.savePasswordPolicy(policy);
        logger.log(ActionType.POLICY_UPDATED, actor, policy.toString());
        return policy;
    }

    /** @throws DomainException POLICY_VIOLATION listing every unmet rule */
    public void validate(String password) {
        List<String> violations = current().violations(password);
        if (!violations.isEmpty()) {
            throw new DomainException(ErrorCode.POLICY_VIOLATION,
                    "Password must contain " + String.join(", ", violations));
        }
    }

    public boolean isExpired(Employee e) {
        return current().isExpired(e.getPasswordChangedAt(), System.currentTimeMillis());
    }
}
