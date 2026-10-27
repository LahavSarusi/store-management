package com.store.server.dao;

import com.store.server.model.InventoryItem;

import java.util.List;

public interface InventoryDao {
    /** @return a detached copy of the branch stock (empty when the branch has none) */
    List<InventoryItem> findByBranch(String branchId);
    /** Replaces the whole stock of a branch. */
    void saveBranch(String branchId, List<InventoryItem> items);
}
