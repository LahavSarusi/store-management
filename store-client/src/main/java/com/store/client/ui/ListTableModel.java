package com.store.client.ui;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Read-only table model over a list of records, with one function per column. */
public final class ListTableModel<T> extends AbstractTableModel {

    public record Column<T>(String name, Class<?> type, Function<T, Object> value) { }

    private final List<Column<T>> columns;
    private List<T> rows = new ArrayList<>();

    @SafeVarargs
    public ListTableModel(Column<T>... columns) {
        this.columns = List.of(columns);
    }

    public static <T> Column<T> col(String name, Function<T, Object> value) {
        return new Column<>(name, Object.class, value);
    }

    /** A column whose class (and so its sorting and alignment) is taken from its values, e.g. Integer. */
    public static <T> Column<T> num(String name, Function<T, Object> value) {
        return new Column<>(name, null, value);
    }

    public void setRows(List<T> newRows) {
        rows = new ArrayList<>(newRows == null ? List.of() : newRows);
        fireTableDataChanged();
    }

    public List<T> rows() {
        return List.copyOf(rows);
    }

    public T rowAt(int modelRow) {
        return rows.get(modelRow);
    }

    /** Replaces the row with the same key, or appends it. */
    public void upsert(T row, Function<T, ?> key) {
        Object k = key.apply(row);
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(key.apply(rows.get(i)), k)) {
                rows.set(i, row);
                fireTableRowsUpdated(i, i);
                return;
            }
        }
        rows.add(row);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).name();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        Column<T> c = columns.get(column);
        if (c.type() != null) {
            return c.type();
        }
        for (T row : rows) {
            Object v = c.value().apply(row);
            if (v != null) {
                return v.getClass();
            }
        }
        return Object.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        return columns.get(column).value().apply(rows.get(row));
    }
}
