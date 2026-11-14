package com.store.client.ui;

import com.store.client.net.ServerException;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Small Swing helpers shared by the screens. */
public final class Ui {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private Ui() { }

    // ---------------------------------------------------------------- messages

    public static String messageOf(Throwable error) {
        Throwable t = Async.unwrap(error);
        if (t instanceof ServerException se) {
            return se.getMessage();
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    public static void showError(Component parent, Throwable error) {
        JOptionPane.showMessageDialog(parent, messageOf(error), "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Confirm", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    // ---------------------------------------------------------------- components

    public static JButton button(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    /** A left-aligned row of components. */
    public static JPanel row(Component... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (Component c : components) {
            p.add(c);
        }
        return p;
    }

    public static JTable table(ListTableModel<?> model) {
        JTable t = new JTable(model);
        t.setAutoCreateRowSorter(true);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return t;
    }

    /** The model row behind the selected (possibly sorted) table row. */
    public static <T> Optional<T> selected(JTable table, ListTableModel<T> model) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return Optional.empty();
        }
        return Optional.of(model.rowAt(table.convertRowIndexToModel(viewRow)));
    }

    /** Renders combo box and list items with {@code text}; null shows as {@code nullText}. */
    public static <T> DefaultListCellRenderer renderer(Function<T, String> text, String nullText) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                @SuppressWarnings("unchecked")
                T item = (T) value;
                setText(item == null ? nullText : text.apply(item));
                return this;
            }
        };
    }

    /** Replaces the items of a combo box, keeping the selection when an item with the same key is still there. */
    public static <T> void setItems(JComboBox<T> combo, List<T> items, Function<T, ?> key) {
        @SuppressWarnings("unchecked")
        T previous = (T) combo.getSelectedItem();
        Object previousKey = previous == null ? null : key.apply(previous);
        DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();
        T reselect = null;
        for (T item : items) {
            model.addElement(item);
            if (item != null && previousKey != null && previousKey.equals(key.apply(item))) {
                reselect = item;
            }
        }
        combo.setModel(model);
        if (reselect != null) {
            combo.setSelectedItem(reselect);
        } else if (previous == null && !items.isEmpty() && items.get(0) == null) {
            combo.setSelectedIndex(0);
        }
    }

    public static JTextArea readOnlyText(int rows, int columns, boolean monospace) {
        JTextArea area = new JTextArea(rows, columns);
        area.setEditable(false);
        if (monospace) {
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        } else {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
        }
        return area;
    }

    public static JScrollPane scroll(Component c) {
        return new JScrollPane(c);
    }

    public static <T extends JComponent> T titled(T component, String title) {
        component.setBorder(BorderFactory.createTitledBorder(title));
        return component;
    }

    /** Builds a two-column "label: field" form. */
    public static final class Form {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        public Form add(String label, Component field) {
            GridBagConstraints l = new GridBagConstraints();
            l.gridx = 0;
            l.gridy = row;
            l.anchor = GridBagConstraints.LINE_END;
            l.insets = new Insets(4, 6, 4, 6);
            panel.add(new JLabel(label), l);

            GridBagConstraints f = new GridBagConstraints();
            f.gridx = 1;
            f.gridy = row;
            f.weightx = 1;
            f.fill = GridBagConstraints.HORIZONTAL;
            f.insets = new Insets(4, 0, 4, 6);
            panel.add(field, f);
            row++;
            return this;
        }

        /** A component that spans both columns. */
        public Form addWide(Component component) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 2;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(4, 6, 4, 6);
            panel.add(component, c);
            return this;
        }

        public JPanel build() {
            return panel;
        }
    }

    // ---------------------------------------------------------------- formatting and parsing

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String money(BigDecimal amount) {
        return amount == null ? "" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Two-decimal amount for table cells, which still sorts as a number. */
    public static BigDecimal amount(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static String dateTime(long epochMillis) {
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String time(long epochMillis) {
        return TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Parses a positive price, or returns null for a blank field. */
    public static BigDecimal parsePrice(String text) {
        String s = blankToNull(text);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Price must be a number, e.g. 79.90");
        }
    }
}
