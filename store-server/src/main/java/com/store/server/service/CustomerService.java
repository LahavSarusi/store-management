package com.store.server.service;

import com.store.server.dao.CustomerDao;
import com.store.server.event.EventBus;
import com.store.server.logging.ActionLogger;
import com.store.server.logging.ActionType;
import com.store.server.model.PurchaseResult;
import com.store.server.model.customer.Customer;
import com.store.server.model.customer.CustomerFactory;
import com.store.server.model.customer.CustomerType;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.EventType;
import com.store.server.protocol.ServerEvent;
import com.store.server.protocol.dto.Dtos.CustomerEvent;
import com.store.server.protocol.dto.Dtos.CustomerRequest;
import com.store.server.util.Validation;

import java.util.Comparator;
import java.util.List;

/** The global customer list. Every change is pushed live to all logged-in employees. */
public class CustomerService {

    private final CustomerDao customerDao;
    private final EventBus bus;
    private final ActionLogger logger;

    public CustomerService(CustomerDao customerDao, EventBus bus, ActionLogger logger) {
        this.customerDao = customerDao;
        this.bus = bus;
        this.logger = logger;
    }

    public List<Customer> list() {
        return customerDao.findAll().stream()
                .sorted(Comparator.comparing(Customer::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Customer get(String id) {
        return customerDao.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found: " + id));
    }

    public synchronized Customer create(String actor, CustomerRequest req) {
        String id = Validation.requireText(req.id(), "id");
        Validation.require(Validation.isValidIsraeliId(id), "Invalid ID number");
        if (customerDao.findById(id).isPresent()) {
            throw new DomainException(ErrorCode.CONFLICT, "A customer with this ID already exists");
        }
        String name = Validation.requireText(req.fullName(), "fullName");
        Validation.require(Validation.isValidPhone(req.phone()), "Invalid phone number");
        CustomerType type = req.type() == null ? CustomerType.NEW : req.type();

        Customer c = CustomerFactory.create(type, id, name, req.phone());
        customerDao.save(c);
        logger.log(ActionType.CUSTOMER_CREATED, actor, c.toString());
        publish(c);
        return c;
    }

    /** Null fields are left unchanged; a new type re-creates the customer as that subclass. */
    public synchronized Customer update(String actor, CustomerRequest req) {
        Customer c = get(Validation.requireText(req.id(), "id"));
        if (req.fullName() != null) {
            c.setFullName(Validation.requireText(req.fullName(), "fullName"));
        }
        if (req.phone() != null) {
            Validation.require(Validation.isValidPhone(req.phone()), "Invalid phone number");
            c.setPhone(req.phone());
        }
        if (req.type() != null) {
            c = CustomerFactory.changeType(c, req.type());
        }
        customerDao.save(c);
        logger.log(ActionType.CUSTOMER_UPDATED, actor, c.toString());
        publish(c);
        return c;
    }

    /** Applies a committed sale to the customer's statistics and upgrades their type when eligible. */
    public synchronized Customer recordPurchase(String actor, String customerId, PurchaseResult result) {
        Customer c = get(customerId);
        c.recordPurchase(result);
        Customer upgraded = CustomerFactory.upgradeIfEligible(c);
        customerDao.save(upgraded);
        if (upgraded.getType() != c.getType()) {
            logger.log(ActionType.CUSTOMER_UPGRADED, actor,
                    customerId + " " + c.getType() + " -> " + upgraded.getType());
        }
        publish(upgraded);
        return upgraded;
    }

    private void publish(Customer c) {
        bus.toAll(ServerEvent.of(EventType.CUSTOMER_UPDATED, new CustomerEvent(c)));
    }
}
