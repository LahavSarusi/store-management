package com.store.server.model;

import java.math.BigDecimal;
import java.util.List;

/** Outcome of a customer's purchasing route: prices after the type-specific discount, plus extra benefits. */
public record PurchaseResult(BigDecimal subtotal, BigDecimal discount, BigDecimal total, List<String> benefits) { }
