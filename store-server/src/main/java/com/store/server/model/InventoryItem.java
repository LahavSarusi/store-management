package com.store.server.model;

import java.math.BigDecimal;

/** A product line in one branch's stock. */
public class InventoryItem {

    private String sku;
    private String name;
    private Category category;
    private BigDecimal price;
    private int quantity;

    public InventoryItem() { }

    public InventoryItem(String sku, String name, Category category, BigDecimal price, int quantity) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.price = price;
        this.quantity = quantity;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public Category getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public InventoryItem copy() {
        return new InventoryItem(sku, name, category, price, quantity);
    }
}
