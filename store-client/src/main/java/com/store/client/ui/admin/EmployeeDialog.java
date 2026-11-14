package com.store.client.ui.admin;

import com.store.client.dto.Dtos.Branch;
import com.store.client.dto.Dtos.EmployeeRequest;
import com.store.client.dto.Dtos.EmployeeView;
import com.store.client.dto.Role;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * EMPLOYEE_CREATE / EMPLOYEE_UPDATE with every employee field the spec requires (req 8a). The employee number
 * is assigned by the server. On update, unchanged fields are sent as null; an empty password keeps the old one.
 */
final class EmployeeDialog extends JDialog {

    private final ServerApi api;
    private final EmployeeView existing;
    private final JTextField username = new JTextField(16);
    private final JPasswordField password = new JPasswordField(16);
    private final JTextField nationalId = new JTextField(12);
    private final JTextField fullName = new JTextField(20);
    private final JTextField phone = new JTextField(14);
    private final JTextField bankAccount = new JTextField(16);
    private final JComboBox<Role> role = new JComboBox<>(Role.values());
    private final JComboBox<Branch> branch = new JComboBox<>();
    private final JButton save = new JButton("Save");
    private boolean saved;

    EmployeeDialog(Window owner, ServerApi api, List<Branch> branches, EmployeeView existing) {
        super(owner, existing == null ? "New Employee" : "Edit Employee", ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.existing = existing;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        branch.setRenderer(Ui.renderer((Branch b) -> b.name() + " (" + b.id() + ")", "-"));
        Ui.setItems(branch, branches, Branch::id);
        role.addActionListener(e -> branch.setEnabled(((Role) role.getSelectedItem()).isBranchBound()));
        role.setSelectedItem(Role.CASHIER);

        Ui.Form form = new Ui.Form();
        if (existing != null) {
            username.setText(existing.username());
            username.setEditable(false);
            nationalId.setText(existing.nationalId());
            fullName.setText(existing.fullName());
            phone.setText(existing.phone());
            bankAccount.setText(existing.bankAccount());
            role.setSelectedItem(existing.role());
            branches.stream().filter(b -> b.id().equals(existing.branchId())).findFirst()
                    .ifPresent(branch::setSelectedItem);
            form.add("Employee number:", new JLabel(String.valueOf(existing.employeeNumber())));
        }
        form.add("Username:", username)
            .add(existing == null ? "Password:" : "New password:", password);
        if (existing != null) {
            form.addWide(new JLabel("Leave the password empty to keep the current one."));
        }
        form.add("ID number:", nationalId)
            .add("Full name:", fullName)
            .add("Phone:", phone)
            .add("Bank account:", bankAccount)
            .add("Role:", role)
            .add("Branch:", branch);
        JPanel body = form.build();
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        save.addActionListener(e -> save());
        getRootPane().setDefaultButton(save);
        add(body, BorderLayout.CENTER);
        add(Ui.row(save, Ui.button("Cancel", e -> dispose())), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    boolean isSaved() {
        return saved;
    }

    private void save() {
        Role selectedRole = (Role) role.getSelectedItem();
        Branch selectedBranch = (Branch) branch.getSelectedItem();
        String branchId = selectedRole.isBranchBound() && selectedBranch != null ? selectedBranch.id() : null;
        String newPassword = Ui.blankToNull(new String(password.getPassword()));

        CompletableFuture<EmployeeView> call;
        if (existing == null) {
            call = api.createEmployee(new EmployeeRequest(Ui.blankToNull(username.getText()), newPassword,
                    Ui.blankToNull(nationalId.getText()), Ui.blankToNull(fullName.getText()),
                    Ui.blankToNull(phone.getText()), Ui.blankToNull(bankAccount.getText()), branchId, selectedRole));
        } else {
            call = api.updateEmployee(new EmployeeRequest(existing.username(), newPassword,
                    changed(existing.nationalId(), nationalId.getText()),
                    changed(existing.fullName(), fullName.getText()),
                    changed(existing.phone(), phone.getText()),
                    changed(existing.bankAccount(), bankAccount.getText()),
                    Objects.equals(branchId, existing.branchId()) ? null : branchId,
                    selectedRole == existing.role() ? null : selectedRole));
        }
        save.setEnabled(false);
        Async.then(call, view -> {
            saved = true;
            dispose();
        }, error -> {
            save.setEnabled(true);
            Ui.showError(this, error);
        });
    }

    private static String changed(String before, String text) {
        String after = text == null ? "" : text.trim();
        return Objects.equals(before == null ? "" : before, after) ? null : after;
    }
}
