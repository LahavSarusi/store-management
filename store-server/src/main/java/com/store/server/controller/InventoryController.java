package com.store.server.controller;

import com.store.server.net.RequestDispatcher;
import com.store.server.protocol.RequestType;
import com.store.server.protocol.Response;
import com.store.server.protocol.dto.Dtos.InventoryView;
import com.store.server.protocol.dto.Dtos.RestockRequest;
import com.store.server.service.InventoryService;

import static com.store.server.controller.Controller.body;

/** Inventory of the caller's own branch (the branch always comes from the session). */
public class InventoryController implements Controller {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Override
    public void registerRoutes(RequestDispatcher d) {
        d.register(RequestType.INVENTORY_LIST, (req, c) -> {
            String branchId = c.requireEmployee().getBranchId();
            return Response.ok(req.requestId(), new InventoryView(branchId, inventory.list(branchId)));
        }).register(RequestType.INVENTORY_RESTOCK, (req, c) ->
                Response.ok(req.requestId(), inventory.restock(c.requireEmployee(), body(req, RestockRequest.class))));
    }
}
