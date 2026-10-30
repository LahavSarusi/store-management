package com.store.client.ui;

import com.store.client.app.Session;
import com.store.client.dto.ErrorCode;
import com.store.client.net.ClientEventBus;
import com.store.client.net.Connection;
import com.store.client.net.ServerApi;
import com.store.client.net.ServerException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Login screen (req 2). Every login opens a new connection, because the server ties the session to the socket.
 * A second login with the same user is refused by the server with ALREADY_LOGGED_IN.
 */
public final class LoginFrame extends JFrame {

    private final String host;
    private final int port;
    private final JTextField username = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private final JButton loginButton = new JButton("Log in");
    private final JLabel status = new JLabel(" ");

    /** What a successful LOGIN hands over to the main window. */
    private record LoggedIn(ServerApi api, ClientEventBus bus, Session session, boolean passwordExpired) { }

    public LoginFrame(String host, int port) {
        super("Clothing Store Network - Login");
        this.host = host;
        this.port = port;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel title = new JLabel("Clothing Store Network", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 16));

        JLabel server = new JLabel("Server: " + host + ":" + port, SwingConstants.CENTER);
        server.setForeground(Color.GRAY);

        JPanel form = new Ui.Form()
                .add("Username:", username)
                .add("Password:", password)
                .addWide(status)
                .build();
        form.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        loginButton.addActionListener(e -> login());
        getRootPane().setDefaultButton(loginButton);

        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.CENTER);
        header.add(server, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(Ui.row(loginButton), BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void login() {
        String user = username.getText().trim();
        String pass = new String(password.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            showStatus("Enter a username and a password.", true);
            return;
        }
        setBusy(true);
        showStatus("Connecting to " + host + ":" + port + "...", false);

        CompletableFuture<LoggedIn> attempt = CompletableFuture.supplyAsync(this::connect).thenCompose(conn -> {
            ClientEventBus bus = new ClientEventBus(conn);
            ServerApi api = new ServerApi(conn);
            return api.login(user, pass)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            conn.close();
                        }
                    })
                    .thenApply(result -> new LoggedIn(api, bus, new Session(result), result.passwordExpired()));
        });
        Async.then(attempt, this::loggedIn, this::failed);
    }

    private Connection connect() {
        try {
            return Connection.open(host, port);
        } catch (IOException e) {
            throw new CompletionException(new ServerException(null,
                    "Cannot reach the server at " + host + ":" + port + ". Make sure it is running."));
        }
    }

    private void loggedIn(LoggedIn li) {
        if (li.passwordExpired()) {
            ChangePasswordDialog dialog = new ChangePasswordDialog(this, li.api(), true);
            dialog.setVisible(true);
            if (!dialog.isChanged()) {
                li.api().logout().whenComplete((v, e) -> li.api().connection().close());
                setBusy(false);
                showStatus("You must change your expired password to continue.", true);
                return;
            }
        }
        new MainFrame(host, port, li.api(), li.bus(), li.session()).setVisible(true);
        dispose();
    }

    private void failed(Throwable error) {
        setBusy(false);
        password.setText("");
        String message = messageFor(error);
        showStatus(message, true);
        if (error instanceof ServerException se && se.is(ErrorCode.ALREADY_LOGGED_IN)) {
            JOptionPane.showMessageDialog(this, message, "Already logged in", JOptionPane.WARNING_MESSAGE);
        }
    }

    static String messageFor(Throwable error) {
        if (error instanceof ServerException se && se.code() != null) {
            return switch (se.code()) {
                case ALREADY_LOGGED_IN -> "This user is already logged in on another computer. Log out there first.";
                case INVALID_CREDENTIALS -> "Wrong username or password.";
                case SERVER_BUSY -> "The server has too many connected users. Try again in a moment.";
                default -> se.getMessage();
            };
        }
        return Ui.messageOf(error);
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        username.setEnabled(!busy);
        password.setEnabled(!busy);
    }

    private void showStatus(String text, boolean error) {
        status.setText("<html><div style='width:260px'>" + escape(text) + "</div></html>");
        status.setForeground(error ? new Color(0xB00020) : Color.DARK_GRAY);
        pack();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
