package com.store.server.dao;

import com.store.server.model.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchDao {
    Optional<Branch> findById(String id);
    List<Branch> findAll();
    void save(Branch branch);
}
