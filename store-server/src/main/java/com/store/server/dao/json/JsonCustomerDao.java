package com.store.server.dao.json;

import com.store.server.dao.CustomerDao;
import com.store.server.model.customer.Customer;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class JsonCustomerDao extends JsonCollectionDao<Customer> implements CustomerDao {

    public JsonCustomerDao(Path file) {
        super(file, Customer.class, Customer::getId);
    }

    @Override
    public Optional<Customer> findById(String id) {
        return get(id);
    }

    @Override
    public List<Customer> findAll() {
        return values();
    }

    @Override
    public void save(Customer customer) {
        put(customer);
    }
}
