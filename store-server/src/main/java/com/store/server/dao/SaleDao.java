package com.store.server.dao;

import com.store.server.model.Sale;

import java.util.List;

public interface SaleDao {
    void save(Sale sale);
    List<Sale> findAll();
}
