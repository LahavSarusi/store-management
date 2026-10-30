package com.store.client.ui.admin;

import com.store.client.dto.Dtos.Branch;
import com.store.client.dto.Dtos.EmployeeView;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.ListTableModel;
import com.store.client.ui.Ui;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.store.client.ui.ListTableModel.col;
import static com.store.client.ui.ListTableModel.num;

/** Employee accounts (req 3, 8): list, create, edit and delete. Admin only. */
public final class EmployeesPanel extends JPanel {

    private final ServerApi api;
    private final List<Branch> branches = new ArrayList<>();
    private final Map<String, String> branchNames = new HashMap<>();
    private final ListTableModel<EmployeeView> model = new ListTableModel<>(
            num("Employee #", EmployeeView::employeeNumber),
            col("Username", EmployeeView::username),
            col("Full name", EmployeeView::fullName),
            col("ID number", EmployeeView::nationalId),
            col("Phone", EmployeeView::phone),
            col("Bank account", EmployeeView::bankAccount),
            col("Branch", e -> e.branchId() == null ? "-" : branchNames.getOrDefault(e.branchId(), e.branchId())),
            col("Role", EmployeeView::role));
    private final JTable table = Ui.table(model);

    public EmployeesPanel(ServerApi api) {
        super(new BorderLayout());
        this.api = api;

        add(Ui.row(Ui.button("Refresh", e -> load()),
                Ui.button("New employee", e -> open(null)),
                Ui.button("Edit selected", e -> withSelected(this::open)),
                Ui.button("Delete selected", e -> withSelected(this::delete))), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    withSelected(EmployeesPanel.this::open);
                }
            }
        });

        Async.then(this, api.branches(), list -> {
            branches.clear();
            branches.addAll(list);
            list.forEach(b -> branchNames.put(b.id(), b.name()));
            model.fireTableDataChanged();
        });
        load();
    }

    private void load() {
        Async.then(this, api.employees(), model::setRows);
    }

    private void withSelected(java.util.function.Consumer<EmployeeView> action) {
        Ui.selected(table, model).ifPresentOrElse(action, () -> Ui.showError(this, "Select an employee first."));
    }

    private void open(EmployeeView existing) {
        EmployeeDialog dialog = new EmployeeDialog(SwingUtilities.getWindowAncestor(this), api, branches, existing);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            load();
        }
    }

    private void delete(EmployeeView e) {
        if (!Ui.confirm(this, "Delete the account of " + e.fullName() + " (" + e.username() + ")?\n"
                + "If they are logged in, they will be logged out.")) {
            return;
        }
        Async.then(this, api.deleteEmployee(e.username()), v -> load());
    }
}
