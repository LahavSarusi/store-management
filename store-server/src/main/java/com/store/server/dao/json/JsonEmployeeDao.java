package com.store.server.dao.json;

import com.store.server.dao.EmployeeDao;
import com.store.server.model.Employee;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class JsonEmployeeDao extends JsonCollectionDao<Employee> implements EmployeeDao {

    public JsonEmployeeDao(Path file) {
        super(file, Employee.class, Employee::getUsername);
    }

    @Override
    public Optional<Employee> findByUsername(String username) {
        return get(username);
    }

    @Override
    public List<Employee> findAll() {
        return values();
    }

    @Override
    public void save(Employee employee) {
        put(employee);
    }

    @Override
    public boolean delete(String username) {
        return remove(username);
    }
}
