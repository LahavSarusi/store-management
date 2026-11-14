package com.store.client.ui.customers;

import com.store.client.dto.CustomerType;
import com.store.client.dto.Dtos.Customer;
import com.store.client.dto.Dtos.CustomerRequest;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Window;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * CUSTOMER_CREATE / CUSTOMER_UPDATE. The table refreshes from the CUSTOMER_UPDATED event that follows, so the
 * dialog only has to send the request. The server validates the ID number and the phone.
 */
final class CustomerDialog extends JDialog {

    private final ServerApi api;
    private final Customer existing;
    private final JTextField id = new JTextField(14);
    private final JTextField name = new JTextField(20);
    private final JTextField phone = new JTextField(14);
    private final JComboBox<CustomerType> type = new JComboBox<>(CustomerType.values());
    private final JButton save = new JButton("Save");

    /** @param existing the customer to edit, or null to create one */
    CustomerDialog(Window owner, ServerApi api, Customer existing) {
        super(owner, existing == null ? "New Customer" : "Edit Customer", ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.existing = existing;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        if (existing != null) {
            id.setText(existing.id());
            id.setEditable(false);
            name.setText(existing.fullName());
            phone.setText(existing.phone());
            type.setSelectedItem(existing.type());
        }

        JPanel form = new Ui.Form()
                .add("ID number:", id)
                .add("Full name:", name)
                .add("Phone:", phone)
                .add("Type:", type)
                .build();
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        save.addActionListener(e -> save());
        getRootPane().setDefaultButton(save);
        add(form, BorderLayout.CENTER);
        add(Ui.row(save, Ui.button("Cancel", e -> dispose())), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private void save() {
        CustomerType selectedType = (CustomerType) type.getSelectedItem();
        CompletableFuture<Customer> call;
        if (existing == null) {
            call = api.createCustomer(new CustomerRequest(Ui.blankToNull(id.getText()),
                    Ui.blankToNull(name.getText()), Ui.blankToNull(phone.getText()), selectedType));
        } else {
            // Only changed fields are sent; null means "unchanged" and a new type re-creates the customer.
            call = api.updateCustomer(new CustomerRequest(existing.id(),
                    changed(existing.fullName(), name.getText()),
                    changed(existing.phone(), phone.getText()),
                    selectedType == existing.type() ? null : selectedType));
        }
        save.setEnabled(false);
        Async.then(call, c -> dispose(), error -> {
            save.setEnabled(true);
            Ui.showError(this, error);
        });
    }

    private static String changed(String before, String text) {
        String after = text == null ? "" : text.trim();
        return Objects.equals(before == null ? "" : before, after) ? null : after;
    }
}
