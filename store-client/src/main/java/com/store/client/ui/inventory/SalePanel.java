package com.store.client.ui.inventory;

import com.store.client.app.Session;
import com.store.client.dto.Dtos.Customer;
import com.store.client.dto.Dtos.CustomerEvent;
import com.store.client.dto.Dtos.InventoryItem;
import com.store.client.dto.Dtos.InventoryView;
import com.store.client.dto.Dtos.PurchaseResult;
import com.store.client.dto.Dtos.SaleLine;
import com.store.client.dto.Dtos.SaleLineRequest;
import com.store.client.dto.Dtos.SaleQuote;
import com.store.client.dto.Dtos.SaleRequest;
import com.store.client.dto.Dtos.SaleResult;
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
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.store.client.ui.ListTableModel.col;
import static com.store.client.ui.ListTableModel.num;

/**
 * Selling (req 5) to a customer (req 6). The server prices the sale with the customer's type-specific discount;
 * a quote shows the price without selling. Users without SELL (salespeople) can only quote.
 */
public final class SalePanel extends JPanel {

    /** One line of the cart, with what we know about the product for display. */
    private record CartLine(String sku, String name, int quantity) { }

    private final ServerApi api;
    private final JComboBox<Customer> customer = new JComboBox<>();
    private final JLabel track = new JLabel(" ");
    private final JComboBox<InventoryItem> product = new JComboBox<>();
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 1_000, 1));
    private final ListTableModel<CartLine> cart = new ListTableModel<>(
            col("SKU", CartLine::sku),
            col("Product", CartLine::name),
            num("Quantity", CartLine::quantity));
    private final JTable cartTable = Ui.table(cart);
    private final JTextArea summary = Ui.readOnlyText(18, 44, true);
    private final JButton quoteButton = new JButton("Get quote");
    private final JButton sellButton = new JButton("Complete sale");
    private final List<Customer> customers = new ArrayList<>();

    public SalePanel(ServerApi api, ClientEventBus bus, Session session) {
        super(new BorderLayout());
        this.api = api;

        customer.setRenderer(Ui.renderer((Customer c) -> c.fullName() + " (" + c.id() + ") - " + c.type(),
                "Choose a customer"));
        customer.addActionListener(e -> showTrack());
        product.setRenderer(Ui.renderer((InventoryItem i) -> i.sku() + " - " + i.name() + " - "
                + Ui.money(i.price()) + " (" + i.quantity() + " in stock)", "Choose a product"));

        JPanel top = new JPanel(new BorderLayout());
        top.add(Ui.row(new JLabel("Customer:"), customer, Ui.button("Reload customers", e -> loadCustomers())),
                BorderLayout.NORTH);
        top.add(Ui.row(track), BorderLayout.CENTER);
        top.add(Ui.row(new JLabel("Product:"), product, new JLabel("Qty:"), quantity,
                Ui.button("Add to cart", e -> addLine())), BorderLayout.SOUTH);

        JPanel cartPanel = new JPanel(new BorderLayout());
        cartPanel.add(Ui.scroll(cartTable), BorderLayout.CENTER);
        quoteButton.addActionListener(e -> quote());
        sellButton.addActionListener(e -> sell());
        JPanel actions = Ui.row(Ui.button("Remove line", e -> removeLine()), Ui.button("Clear cart", e -> clear()),
                quoteButton);
        if (session.has(Permission.SELL)) {
            actions.add(sellButton);
        } else {
            actions.add(new JLabel("Your role can quote prices but not complete sales."));
        }
        cartPanel.add(actions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                Ui.titled(cartPanel, "Cart"), Ui.titled(Ui.scroll(summary), "Quote / receipt"));
        split.setResizeWeight(0.5);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        bus.on(EventType.INVENTORY_UPDATED, InventoryView.class, this::showProducts);
        bus.on(EventType.CUSTOMER_UPDATED, CustomerEvent.class, e -> {
            if (e != null && e.customer() != null) {
                customers.removeIf(c -> c.id().equals(e.customer().id()));
                customers.add(e.customer());
                showCustomers();
            }
        });
        loadCustomers();
        Async.then(this, api.inventory(), this::showProducts);
    }

    // ---------------------------------------------------------------- data

    private void loadCustomers() {
        Async.then(this, api.customers(), list -> {
            customers.clear();
            customers.addAll(list);
            showCustomers();
        });
    }

    private void showCustomers() {
        List<Customer> items = new ArrayList<>();
        items.add(null);
        customers.stream().sorted((a, b) -> a.fullName().compareToIgnoreCase(b.fullName())).forEach(items::add);
        Ui.setItems(customer, items, Customer::id);
        showTrack();
    }

    private void showProducts(InventoryView view) {
        List<InventoryItem> items = new ArrayList<>();
        items.add(null);
        if (view != null && view.items() != null) {
            items.addAll(view.items());
        }
        Ui.setItems(product, items, InventoryItem::sku);
    }

    private void showTrack() {
        Customer c = (Customer) customer.getSelectedItem();
        track.setText(c == null ? " " : "<html><i>" + c.type() + " track:</i> " + c.track() + "</html>");
    }

    // ---------------------------------------------------------------- cart

    private void addLine() {
        InventoryItem item = (InventoryItem) product.getSelectedItem();
        if (item == null) {
            Ui.showError(this, "Choose a product first.");
            return;
        }
        int qty = (Integer) quantity.getValue();
        List<CartLine> lines = new ArrayList<>(cart.rows());
        CartLine existing = lines.stream().filter(l -> l.sku().equals(item.sku())).findFirst().orElse(null);
        if (existing != null) {
            lines.set(lines.indexOf(existing), new CartLine(item.sku(), item.name(), existing.quantity() + qty));
        } else {
            lines.add(new CartLine(item.sku(), item.name(), qty));
        }
        cart.setRows(lines);
        quantity.setValue(1);
    }

    private void removeLine() {
        Ui.selected(cartTable, cart).ifPresent(line -> {
            List<CartLine> lines = new ArrayList<>(cart.rows());
            lines.remove(line);
            cart.setRows(lines);
        });
    }

    private void clear() {
        cart.setRows(List.of());
        summary.setText("");
    }

    private SaleRequest request() {
        Customer c = (Customer) customer.getSelectedItem();
        if (c == null) {
            Ui.showError(this, "Choose the customer for this sale.");
            return null;
        }
        if (cart.getRowCount() == 0) {
            Ui.showError(this, "The cart is empty.");
            return null;
        }
        List<SaleLineRequest> lines = cart.rows().stream()
                .map(l -> new SaleLineRequest(l.sku(), l.quantity())).toList();
        return new SaleRequest(c.id(), lines);
    }

    // ---------------------------------------------------------------- server calls

    private void quote() {
        SaleRequest request = request();
        if (request == null) {
            return;
        }
        quoteButton.setEnabled(false);
        Async.then(api.quote(request), q -> {
            quoteButton.setEnabled(true);
            summary.setText(describeQuote(q));
            summary.setCaretPosition(0);
        }, error -> {
            quoteButton.setEnabled(true);
            Ui.showError(this, error);
        });
    }

    private void sell() {
        SaleRequest request = request();
        if (request == null || !Ui.confirm(this, "Complete this sale?")) {
            return;
        }
        Customer before = (Customer) customer.getSelectedItem();
        sellButton.setEnabled(false);
        Async.then(api.sell(request), result -> {
            sellButton.setEnabled(true);
            cart.setRows(List.of());
            summary.setText(describeSale(result, before));
            summary.setCaretPosition(0);
        }, error -> {
            sellButton.setEnabled(true);
            Ui.showError(this, error);
        });
    }

    private String describeQuote(SaleQuote q) {
        Customer c = (Customer) customer.getSelectedItem();
        StringBuilder sb = new StringBuilder("QUOTE (nothing was sold)\n\n");
        sb.append("Customer: ").append(c == null ? q.customerId() : c.fullName() + " (" + c.id() + ")")
          .append(" - ").append(q.customerType()).append("\n\n");
        appendLines(sb, q.lines());
        appendTotals(sb, q.result());
        return sb.toString();
    }

    private static String describeSale(SaleResult r, Customer before) {
        StringBuilder sb = new StringBuilder("SALE COMPLETED\n\n");
        sb.append("Sale: ").append(r.sale().id()).append("   ").append(Ui.dateTime(r.sale().timestamp())).append('\n');
        sb.append("Customer: ").append(r.customer().fullName()).append(" (").append(r.customer().id()).append(")\n\n");
        appendLines(sb, r.sale().lines());
        appendTotals(sb, new PurchaseResult(r.sale().subtotal(), r.sale().discount(), r.sale().total(), r.benefits()));
        if (before != null && !Objects.equals(before.type(), r.customer().type())) {
            sb.append("\nThe customer was upgraded from ").append(before.type())
              .append(" to ").append(r.customer().type()).append("!\n");
        }
        sb.append("\nCustomer now: ").append(r.customer().type()).append(", ")
          .append(r.customer().purchaseCount()).append(" purchases, total ")
          .append(Ui.money(r.customer().totalSpent())).append('\n');
        return sb.toString();
    }

    private static void appendLines(StringBuilder sb, List<SaleLine> lines) {
        sb.append(String.format("%-10s %-22s %5s %10s %11s%n", "SKU", "Product", "Qty", "Price", "Line total"));
        for (SaleLine l : lines) {
            sb.append(String.format("%-10s %-22s %5d %10s %11s%n", l.sku(), cut(l.productName(), 22),
                    l.quantity(), Ui.money(l.unitPrice()), Ui.money(l.lineTotal())));
        }
        sb.append('\n');
    }

    private static void appendTotals(StringBuilder sb, PurchaseResult r) {
        sb.append(String.format("%-20s %12s%n", "Subtotal:", Ui.money(r.subtotal())));
        sb.append(String.format("%-20s %12s%n", "Discount:", "-" + Ui.money(r.discount())));
        sb.append(String.format("%-20s %12s%n", "Total to pay:", Ui.money(r.total())));
        if (r.benefits() != null && !r.benefits().isEmpty()) {
            sb.append("\nBenefits:\n");
            r.benefits().forEach(b -> sb.append("  - ").append(b).append('\n'));
        }
    }

    private static String cut(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }
}
