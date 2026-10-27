package com.store.server.dao.json;

import com.store.server.dao.SaleDao;
import com.store.server.model.Sale;

import java.nio.file.Path;
import java.util.List;

public class JsonSaleDao extends JsonCollectionDao<Sale> implements SaleDao {

    public JsonSaleDao(Path file) {
        super(file, Sale.class, Sale::id);
    }

    @Override
    public void save(Sale sale) {
        put(sale);
    }

    @Override
    public List<Sale> findAll() {
        return values();
    }
}
