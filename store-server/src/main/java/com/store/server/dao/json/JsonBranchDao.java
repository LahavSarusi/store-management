package com.store.server.dao.json;

import com.store.server.dao.BranchDao;
import com.store.server.model.Branch;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class JsonBranchDao extends JsonCollectionDao<Branch> implements BranchDao {

    public JsonBranchDao(Path file) {
        super(file, Branch.class, Branch::id);
    }

    @Override
    public Optional<Branch> findById(String id) {
        return get(id);
    }

    @Override
    public List<Branch> findAll() {
        return values();
    }

    @Override
    public void save(Branch branch) {
        put(branch);
    }
}
