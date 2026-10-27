package com.store.server.model;

import com.store.server.model.customer.CustomerType;

import java.math.BigDecimal;
import java.util.List;

/** A completed sale, persisted for reports. */
public record Sale(String id,
                   String branchId,
                   String employeeUsername,
                   String customerId,
                   CustomerType customerType,
                   List<SaleLine> lines,
                   BigDecimal subtotal,
                   BigDecimal discount,
                   BigDecimal total,
                   long timestamp) { }
