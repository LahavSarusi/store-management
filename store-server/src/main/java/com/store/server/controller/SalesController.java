package com.store.server.controller;

import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.protocol.dto.Dtos.SaleRequest;
import com.store.server.service.SalesService;

import static com.store.server.controller.Controller.body;

/** Price quotes and sales in the caller's branch. */
public class SalesController implements Controller {

    private final SalesService sales;

    public SalesController(SalesService sales) {
        this.sales = sales;
    }

    @Override
    public void registerRoutes(RequestDispatcher d) {
        d.register(RequestType.SALE_QUOTE, (req, c) ->
                 Response.ok(req.requestId(), sales.quote(c.requireEmployee(), body(req, SaleRequest.class))))
         .register(RequestType.SALE_CREATE, (req, c) ->
                 Response.ok(req.requestId(), sales.sell(c.requireEmployee(), body(req, SaleRequest.class))));
    }
}
