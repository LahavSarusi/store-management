package com.store.server.dao;

import com.store.server.model.customer.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerDao {
    Optional<Customer> findById(String id);
    List<Customer> findAll();
    /** Insert or update by id (also used to replace a customer with an instance of another type). */
    void save(Customer customer);
}
