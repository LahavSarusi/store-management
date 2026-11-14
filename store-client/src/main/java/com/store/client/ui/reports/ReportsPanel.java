package com.store.client.ui.reports;

import static com.store.client.ui.ListTableModel.col;
import static com.store.client.ui.ListTableModel.num;

import com.store.client.app.ReportOpener;
import com.store.client.app.Session;
import com.store.client.dto.Category;
import com.store.client.dto.Dtos.Branch;
import com.store.client.dto.Dtos.ReportRequest;
import com.store.client.dto.Dtos.ReportResult;
import com.store.client.dto.Dtos.SalesReport;
import com.store.client.dto.GroupBy;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.ListTableModel;
import com.store.client.ui.Ui;

import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Sales reports (req 7): by branch, product or category, filtered by products, categories and dates, with a
 * daily report when a single day is given. The server computes the report and its text; "Open in Notepad"
 * exports it (REPORT_EXPORT), saves the text locally and opens it in the system's plain-text editor.
 * Admins choose a branch or the whole network; other users always get their own branch (enforced by the server).
 */
public final class ReportsPanel extends JPanel {
    private final ServerApi api;
    private final Session session;

    private final JComboBox<GroupBy> groupBy = new JComboBox<>(GroupBy.values());
    private final JComboBox<Branch> branch = new JComboBox<>();
    private final Map<Category, JCheckBox> categories = new EnumMap<>(Category.class);
    private final JTextField skus = new JTextField(18);
    private final JTextField day = new JTextField(9);
    private final JTextField from = new JTextField(9);
    private final JTextField to = new JTextField(9);
    private final JButton showButton = new JButton("Show report");
    private final JButton openButton = new JButton("Open in " + ReportOpener.editorName());
    private final JLabel status = new JLabel(" ");

    private final ListTableModel<SalesReport.Row> rows = new ListTableModel<>(
            col("Key", SalesReport.Row::key),
            col("Name", SalesReport.Row::label),
            num("Units", SalesReport.Row::units),
            num("Sales", SalesReport.Row::sales),
            num("Gross", r -> Ui.amount(r.grossRevenue())),
            num("Net", r -> Ui.amount(r.netRevenue())));
    private final JTable table = Ui.table(rows);
    private final JTextArea text = Ui.readOnlyText(14, 80, true);

    public ReportsPanel(ServerApi api, Session session) {
        super(new BorderLayout());
        this.api = api;
        this.session = session;

        JPanel scope = Ui.row(new JLabel("Group by:"), groupBy);
        if (session.isAdmin()) {
            branch.setRenderer(Ui.renderer((Branch b) -> b.name() + " (" + b.id() + ")", "All branches"));
            scope.add(new JLabel("Branch:"));
            scope.add(branch);
            Async.then(this, api.branches(), this::showBranches);
        } else {
            scope.add(new JLabel("Branch: " + (session.branch() == null ? "-" : session.branch().name())));
        }
        scope.add(new JLabel("Products (SKUs, comma separated):"));
        scope.add(skus);

        JPanel categoryRow = Ui.row(new JLabel("Categories (none = all):"));
        for (Category c : Category.values()) {
            JCheckBox box = new JCheckBox(c.name());
            categories.put(c, box);
            categoryRow.add(box);
        }

        showButton.addActionListener(e -> showReport());
        openButton.addActionListener(e -> export());
        JPanel dates = Ui.row(new JLabel("Day:"), day, new JLabel("or  From:"), from, new JLabel("To:"), to,
                new JLabel("(yyyy-MM-dd, optional)"), Ui.button("Today", e -> today()),
                showButton, openButton);

        JPanel filters = new JPanel(new BorderLayout());
        filters.add(scope, BorderLayout.NORTH);
        filters.add(categoryRow, BorderLayout.CENTER);
        filters.add(dates, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Ui.scroll(table),
                Ui.titled(Ui.scroll(text), "Report text"));
        split.setResizeWeight(0.5);

        add(Ui.titled(filters, "Sales report"), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);   // full width, so a long filter description is never clipped
    }

    private void showBranches(List<Branch> branches) {
        List<Branch> items = new ArrayList<>();
        items.add(null);   // all branches
        items.addAll(branches);
        Ui.setItems(branch, items, Branch::id);
    }

    private void today() {
        day.setText(java.time.LocalDate.now().toString());
        from.setText("");
        to.setText("");
    }

    /** The filters as a request. Dates are validated by the server; non-admins get their own branch there. */
    private ReportRequest request() {
        Branch selected = session.isAdmin() ? (Branch) branch.getSelectedItem() : null;
        List<String> skuList = Arrays.stream(skus.getText().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<Category> chosen = categories.entrySet().stream()
                .filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
        return new ReportRequest((GroupBy) groupBy.getSelectedItem(),
                selected == null ? null : selected.id(),
                skuList.isEmpty() ? null : skuList,
                chosen.isEmpty() ? null : chosen,
                Ui.blankToNull(day.getText()), Ui.blankToNull(from.getText()), Ui.blankToNull(to.getText()));
    }

    private void showReport() {
        showButton.setEnabled(false);
        Async.then(api.report(request()), (ReportResult result) -> {
            showButton.setEnabled(true);
            SalesReport report = result.report();
            rows.setRows(report.rows());
            text.setText(result.text());
            text.setCaretPosition(0);
            status.setText(report.totalSales() + " sales, " + report.totalUnits() + " units, net "
                    + Ui.money(report.totalNet()) + "  -  " + report.filters());
        }, error -> {
            showButton.setEnabled(true);
            Ui.showError(this, error);
        });
    }

    private void export() {
        openButton.setEnabled(false);
        Async.then(api.exportReport(request()), export -> {
            openButton.setEnabled(true);
            text.setText(export.text());
            text.setCaretPosition(0);
            try {
                Path file = ReportOpener.save(export.fileName(), export.text());
                ReportOpener.open(file);
                status.setText("Report saved to " + file);
            } catch (IOException e) {
                Ui.showError(this, "The report could not be opened: " + e.getMessage());
            }
        }, error -> {
            openButton.setEnabled(true);
            Ui.showError(this, error);
        });
    }
}
