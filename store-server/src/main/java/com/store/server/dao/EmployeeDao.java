package com.store.server.dao;

import com.store.server.model.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeDao {
    Optional<Employee> findByUsername(String username);
    List<Employee> findAll();
    /** Insert or update by username. */
    void save(Employee employee);
    boolean delete(String username);
}
