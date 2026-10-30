package com.store.client.ui.admin;

import com.store.client.dto.ActionType;
import com.store.client.dto.Dtos.LogEntry;
import com.store.client.dto.Dtos.LogQueryRequest;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.ListTableModel;
import com.store.client.ui.Ui;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import static com.store.client.ui.ListTableModel.col;

/**
 * Logs (req 10): the action log filtered by action type and dates, and the chat transcripts that were saved
 * when a participant chose to. Admin only.
 */
public final class LogsPanel extends JPanel {

    private final ServerApi api;

    private final JComboBox<ActionType> type = new JComboBox<>();
    private final JTextField from = new JTextField(9);
    private final JTextField to = new JTextField(9);
    private final JSpinner limit = new JSpinner(new SpinnerNumberModel(200, 1, 1000, 50));
    private final ListTableModel<LogEntry> entries = new ListTableModel<>(
            col("Time", LogEntry::timestamp),
            col("Action", LogEntry::action),
            col("By", LogEntry::actor),
            col("Details", LogEntry::details));
    private final JTable table = Ui.table(entries);

    private final JList<String> transcripts = new JList<>();
    private final JTextArea transcript = Ui.readOnlyText(20, 60, true);

    public LogsPanel(ServerApi api) {
        super(new BorderLayout());
        this.api = api;

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Action log", actionLog());
        tabs.addTab("Chat transcripts", transcriptsView());
        add(tabs, BorderLayout.CENTER);

        search();
        loadTranscripts();
    }

    private JPanel actionLog() {
        List<ActionType> types = new ArrayList<>();
        types.add(null);
        types.addAll(List.of(ActionType.values()));
        type.setRenderer(Ui.renderer((ActionType t) -> t.name(), "All actions"));
        Ui.setItems(type, types, t -> t);

        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(3).setPreferredWidth(520);

        JPanel p = new JPanel(new BorderLayout());
        p.add(Ui.row(new JLabel("Action:"), type, new JLabel("From:"), from, new JLabel("To:"), to,
                new JLabel("(yyyy-MM-dd, optional)  Max rows:"), limit, Ui.button("Search", e -> search())),
                BorderLayout.NORTH);
        p.add(Ui.scroll(table), BorderLayout.CENTER);
        return p;
    }

    private JPanel transcriptsView() {
        transcripts.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        transcripts.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && transcripts.getSelectedValue() != null) {
                Async.then(this, api.transcript(transcripts.getSelectedValue()), t -> {
                    transcript.setText(t.text());
                    transcript.setCaretPosition(0);
                });
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.add(Ui.scroll(transcripts), BorderLayout.CENTER);
        left.add(Ui.row(Ui.button("Refresh", e -> loadTranscripts())), BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(220, 0));

        JPanel p = new JPanel(new BorderLayout());
        p.add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, Ui.scroll(transcript)), BorderLayout.CENTER);
        return p;
    }

    private void search() {
        LogQueryRequest query = new LogQueryRequest((ActionType) type.getSelectedItem(),
                Ui.blankToNull(from.getText()), Ui.blankToNull(to.getText()), (Integer) limit.getValue());
        Async.then(this, api.logs(query), entries::setRows);
    }

    private void loadTranscripts() {
        Async.then(this, api.transcripts(), list -> transcripts.setListData(list.toArray(new String[0])));
    }
}
