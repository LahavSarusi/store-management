package com.store.client.ui;

import com.store.client.net.ServerApi;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.BorderLayout;
import java.awt.Window;
import java.util.Arrays;

/** PASSWORD_CHANGE. The server checks the current password and the password policy. */
public final class ChangePasswordDialog extends JDialog {

    private final ServerApi api;
    private final JPasswordField current = new JPasswordField(18);
    private final JPasswordField next = new JPasswordField(18);
    private final JPasswordField confirm = new JPasswordField(18);
    private final JButton save = new JButton("Change password");
    private boolean changed;

    /** @param expired true when the password expired and must be changed before continuing */
    public ChangePasswordDialog(Window owner, ServerApi api, boolean expired) {
        super(owner, "Change Password", ModalityType.APPLICATION_MODAL);
        this.api = api;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        Ui.Form form = new Ui.Form();
        if (expired) {
            form.addWide(new JLabel("Your password has expired. Please choose a new one."));
        }
        form.add("Current password:", current)
            .add("New password:", next)
            .add("Repeat new password:", confirm);
        JPanel body = form.build();
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        save.addActionListener(e -> submit());
        JButton cancel = Ui.button("Cancel", e -> dispose());
        getRootPane().setDefaultButton(save);

        add(body, BorderLayout.CENTER);
        add(Ui.row(save, cancel), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isChanged() {
        return changed;
    }

    private void submit() {
        char[] newPassword = next.getPassword();
        if (newPassword.length == 0) {
            Ui.showError(this, "Enter a new password.");
            return;
        }
        if (!Arrays.equals(newPassword, confirm.getPassword())) {
            Ui.showError(this, "The two new passwords do not match.");
            return;
        }
        save.setEnabled(false);
        Async.then(api.changePassword(new String(current.getPassword()), new String(newPassword)),
                ok -> {
                    changed = true;
                    Ui.info(this, "Your password was changed.");
                    dispose();
                },
                error -> {
                    save.setEnabled(true);
                    Ui.showError(this, error);
                });
    }
}
