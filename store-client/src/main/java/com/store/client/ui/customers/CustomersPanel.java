package com.store.client.ui.customers;

import com.store.client.app.Session;
import com.store.client.dto.Dtos.Customer;
import com.store.client.dto.Dtos.CustomerEvent;
import com.store.client.dto.EventType;
import com.store.client.dto.Permission;
import com.store.client.net.ClientEventBus;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.ListTableModel;
import com.store.client.ui.Ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.store.client.ui.ListTableModel.col;
import static com.store.client.ui.ListTableModel.num;

/**
 * Customers of the whole network (req 6): details, type and the purchasing track of that type. Kept live for
 * every user through CUSTOMER_UPDATED.
 */
public final class CustomersPanel extends JPanel {

    private final ServerApi api;
    private final boolean canEdit;
    private final ListTableModel<Customer> model = new ListTableModel<>(
            col("ID number", Customer::id),
            col("Name", Customer::fullName),
            col("Phone", Customer::phone),
            col("Type", Customer::type),
            num("Purchases", Customer::purchaseCount),
            num("Total spent", c -> Ui.amount(c.totalSpent())),
            col("Track", Customer::track));
    private final JTable table = Ui.table(model);
    private final JTextArea track = Ui.readOnlyText(4, 40, false);

    public CustomersPanel(ServerApi api, ClientEventBus bus, Session session) {
        super(new BorderLayout());
        this.api = api;
        this.canEdit = session.has(Permission.EDIT_CUSTOMERS);

        JPanel buttons = Ui.row(Ui.button("Refresh", e -> load()));
        if (canEdit) {
            buttons.add(Ui.button("New customer", e -> create()));
            buttons.add(Ui.button("Edit selected", e -> edit()));
        }
        buttons.add(new JLabel("Changes made in any branch appear here immediately."));

        table.getSelectionModel().addListSelectionListener(e -> showTrack());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (canEdit && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    edit();
                }
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Ui.scroll(table),
                Ui.titled(Ui.scroll(track), "Purchasing track of the selected customer"));
        split.setResizeWeight(0.8);

        add(buttons, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        bus.on(EventType.CUSTOMER_UPDATED, CustomerEvent.class, e -> {
            if (e != null && e.customer() != null) {
                model.upsert(e.customer(), Customer::id);
                showTrack();
            }
        });
        load();
    }

    private void load() {
        Async.then(this, api.customers(), model::setRows);
    }

    private void showTrack() {
        track.setText(Ui.selected(table, model)
                .map(c -> c.fullName() + " is a " + c.type() + " customer.\n" + c.track())
                .orElse(""));
        track.setCaretPosition(0);
    }

    private void create() {
        new CustomerDialog(SwingUtilities.getWindowAncestor(this), api, null).setVisible(true);
    }

    private void edit() {
        Ui.selected(table, model).ifPresentOrElse(
                c -> new CustomerDialog(SwingUtilities.getWindowAncestor(this), api, c).setVisible(true),
                () -> Ui.showError(this, "Select a customer first."));
    }
}
