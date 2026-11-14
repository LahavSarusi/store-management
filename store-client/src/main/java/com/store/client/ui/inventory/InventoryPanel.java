package com.store.client.ui.inventory;

import com.store.client.app.Session;
import com.store.client.dto.Category;
import com.store.client.dto.Dtos.InventoryItem;
import com.store.client.dto.Dtos.InventoryView;
import com.store.client.dto.Dtos.RestockRequest;
import com.store.client.dto.EventType;
import com.store.client.dto.Permission;
import com.store.client.net.ClientEventBus;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.ListTableModel;
import com.store.client.ui.Ui;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static com.store.client.ui.ListTableModel.col;
import static com.store.client.ui.ListTableModel.num;

/**
 * Branch inventory (req 5): the stock of the user's own branch, kept live by INVENTORY_UPDATED, and restocking
 * ("buying" from a supplier) for users with RESTOCK_INVENTORY.
 */
public final class InventoryPanel extends JPanel {

    private final ServerApi api;
    private final ListTableModel<InventoryItem> model = new ListTableModel<>(
            col("SKU", InventoryItem::sku),
            col("Name", InventoryItem::name),
            col("Category", InventoryItem::category),
            num("Price", i -> Ui.amount(i.price())),
            num("In stock", InventoryItem::quantity));
    private final JTable table = Ui.table(model);
    private final JLabel status = new JLabel(" ");

    private final JTextField sku = new JTextField(10);
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 100_000, 1));
    private final JTextField name = new JTextField(14);
    private final JComboBox<Category> category = new JComboBox<>(Category.values());
    private final JTextField price = new JTextField(7);
    private final JButton restock = new JButton("Restock");

    public InventoryPanel(ServerApi api, ClientEventBus bus, Session session) {
        super(new BorderLayout());
        this.api = api;

        String branch = session.branch() == null ? "" : session.branch().name();
        add(Ui.row(new JLabel("Stock of branch: " + branch), Ui.button("Refresh", e -> load()), status),
                BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);

        if (session.has(Permission.RESTOCK_INVENTORY)) {
            add(restockForm(), BorderLayout.SOUTH);
            table.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    Ui.selected(table, model).ifPresent(item -> sku.setText(item.sku()));
                }
            });
        }

        bus.on(EventType.INVENTORY_UPDATED, InventoryView.class, view -> {
            show(view);
            status.setText("Updated live at " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        });
        load();
    }

    private JPanel restockForm() {
        restock.addActionListener(e -> restock());
        JPanel form = new JPanel(new BorderLayout());
        form.add(Ui.row(new JLabel("SKU:"), sku, new JLabel("Quantity:"), quantity, restock), BorderLayout.NORTH);
        form.add(Ui.row(new JLabel("New product only -  Name:"), name, new JLabel("Category:"), category,
                new JLabel("Price:"), price, new JLabel("(a price for an existing SKU updates its price)")),
                BorderLayout.SOUTH);
        return Ui.titled(form, "Restock (buy from supplier)");
    }

    private void load() {
        Async.then(this, api.inventory(), this::show);
    }

    private void show(InventoryView view) {
        model.setRows(view == null ? null : view.items());
    }

    private void restock() {
        String code = Ui.blankToNull(sku.getText());
        if (code == null) {
            Ui.showError(this, "Enter the SKU to restock, or pick a product in the table.");
            return;
        }
        BigDecimal newPrice;
        try {
            newPrice = Ui.parsePrice(price.getText());
        } catch (IllegalArgumentException ex) {
            Ui.showError(this, ex.getMessage());
            return;
        }
        boolean known = model.rows().stream().anyMatch(i -> i.sku().equals(code));
        RestockRequest request = new RestockRequest(code, (Integer) quantity.getValue(),
                known ? null : Ui.blankToNull(name.getText()),
                known ? null : (Category) category.getSelectedItem(),
                newPrice);
        restock.setEnabled(false);
        Async.then(api.restock(request), item -> {
            restock.setEnabled(true);
            name.setText("");
            price.setText("");
            quantity.setValue(1);
            Ui.info(this, item.name() + " (" + item.sku() + ") now has " + item.quantity() + " in stock.");
        }, error -> {
            restock.setEnabled(true);
            Ui.showError(this, error);
        });
    }
}
