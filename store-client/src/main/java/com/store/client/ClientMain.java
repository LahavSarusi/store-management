package com.store.client;

import com.store.client.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point: {@code java -jar store-client.jar [host=localhost] [port=5050]}.
 * Start the server first; open several clients to see live updates and chat.
 */
public final class ClientMain {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 5050;

    private ClientMain() { }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep the default look and feel
            }
            new LoginFrame(host, port).setVisible(true);
        });
    }
}
