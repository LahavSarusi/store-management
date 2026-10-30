package com.store.client.ui;

import com.store.client.app.Screen;
import com.store.client.app.Session;
import com.store.client.dto.Dtos.ForcedLogout;
import com.store.client.dto.EventType;
import com.store.client.net.ClientEventBus;
import com.store.client.net.ServerApi;
import com.store.client.ui.admin.EmployeesPanel;
import com.store.client.ui.admin.LogsPanel;
import com.store.client.ui.admin.PasswordPolicyPanel;
import com.store.client.ui.chat.ChatPanel;
import com.store.client.ui.customers.CustomersPanel;
import com.store.client.ui.inventory.InventoryPanel;
import com.store.client.ui.inventory.SalePanel;
import com.store.client.ui.reports.ReportsPanel;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.TimeUnit;

/**
 * The main window after login: a header with the user and one tab per screen the user's permissions allow (req 4).
 * When the server forces a logout or the connection drops, it closes and returns to the login screen.
 */
public final class MainFrame extends JFrame {

    private final String host;
    private final int port;
    private final ServerApi api;
    private boolean ended;

    public MainFrame(String host, int port, ServerApi api, ClientEventBus bus, Session session) {
        super("Clothing Store Network - " + session.employee().fullName());
        this.host = host;
        this.port = port;
        this.api = api;

        JLabel who = new JLabel(session.describe());
        who.setFont(who.getFont().deriveFont(Font.BOLD));
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        header.add(who, BorderLayout.CENTER);
        header.add(Ui.row(
                Ui.button("Change password", e -> new ChangePasswordDialog(this, api, false).setVisible(true)),
                Ui.button("Log out", e -> logout())), BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        for (Screen screen : session.screens()) {
            tabs.addTab(screen.title(), create(screen, api, bus, session));
        }
        if (tabs.getTabCount() == 0) {
            tabs.addTab("Welcome", new JLabel("Your account has no screens assigned.", JLabel.CENTER));
        }

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        bus.on(EventType.FORCED_LOGOUT, ForcedLogout.class, f ->
                endSession("You were logged out by the server:\n" + (f == null ? "" : f.reason())));
        api.connection().addDisconnectListener(() -> SwingUtilities.invokeLater(() ->
                endSession("The connection to the server was lost.")));

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        setPreferredSize(new Dimension(1150, 740));
        pack();
        setLocationRelativeTo(null);

        // Every handler is registered now; deliver events that arrived since login.
        bus.start();
    }

    private static JComponent create(Screen screen, ServerApi api, ClientEventBus bus, Session session) {
        return switch (screen) {
            case INVENTORY -> new InventoryPanel(api, bus, session);
            case SELL -> new SalePanel(api, bus, session);
            case CUSTOMERS -> new CustomersPanel(api, bus, session);
            case REPORTS -> new ReportsPanel(api, session);
            case CHAT -> new ChatPanel(api, bus, session);
            case EMPLOYEES -> new EmployeesPanel(api);
            case PASSWORD_POLICY -> new PasswordPolicyPanel(api);
            case LOGS -> new LogsPanel(api);
        };
    }

    private void logout() {
        if (!Ui.confirm(this, "Log out?")) {
            return;
        }
        ended = true;
        api.logout().orTimeout(2, TimeUnit.SECONDS).whenComplete((v, e) -> SwingUtilities.invokeLater(() -> {
            api.connection().close();
            dispose();
            new LoginFrame(host, port).setVisible(true);
        }));
    }

    private void exit() {
        ended = true;
        api.logout().orTimeout(2, TimeUnit.SECONDS).whenComplete((v, e) -> {
            api.connection().close();
            System.exit(0);
        });
    }

    /** Ends the session after the server closed it, with an explanation, and shows the login screen again. */
    private void endSession(String message) {
        if (ended) {
            return;
        }
        ended = true;
        api.connection().close();
        dispose();
        JOptionPane.showMessageDialog(null, message, "Logged out", JOptionPane.WARNING_MESSAGE);
        new LoginFrame(host, port).setVisible(true);
    }
}
