package com.store.client.ui.admin;

import com.store.client.dto.Dtos.PasswordPolicy;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.Ui;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;

/** Password policy (req 3): applies to new passwords; expiry forces a change at the next login. Admin only. */
public final class PasswordPolicyPanel extends JPanel {

    private final ServerApi api;
    private final JSpinner minLength = new JSpinner(new SpinnerNumberModel(8, 1, 64, 1));
    private final JCheckBox requireDigit = new JCheckBox("At least one digit");
    private final JCheckBox requireUppercase = new JCheckBox("At least one uppercase letter");
    private final JCheckBox requireSpecial = new JCheckBox("At least one special character");
    private final JSpinner expiryDays = new JSpinner(new SpinnerNumberModel(90, 0, 3650, 1));
    private final JButton save = new JButton("Save policy");

    public PasswordPolicyPanel(ServerApi api) {
        super(new BorderLayout());
        this.api = api;

        save.addActionListener(e -> save());
        JPanel form = new Ui.Form()
                .add("Minimum length:", minLength)
                .add("Must contain:", requireDigit)
                .add("", requireUppercase)
                .add("", requireSpecial)
                .add("Expires after (days):", Ui.row(expiryDays, new JLabel("0 = never expires")))
                .addWide(Ui.row(save, Ui.button("Reload", e -> load())))
                .build();
        add(Ui.titled(form, "Password policy"), BorderLayout.NORTH);
        load();
    }

    private void load() {
        Async.then(this, api.passwordPolicy(), this::show);
    }

    private void show(PasswordPolicy p) {
        minLength.setValue(p.minLength());
        requireDigit.setSelected(p.requireDigit());
        requireUppercase.setSelected(p.requireUppercase());
        requireSpecial.setSelected(p.requireSpecial());
        expiryDays.setValue(p.expiryDays());
    }

    private void save() {
        PasswordPolicy policy = new PasswordPolicy((Integer) minLength.getValue(), requireDigit.isSelected(),
                requireUppercase.isSelected(), requireSpecial.isSelected(), (Integer) expiryDays.getValue());
        save.setEnabled(false);
        Async.then(api.setPasswordPolicy(policy), saved -> {
            save.setEnabled(true);
            show(saved);
            Ui.info(this, "The password policy was saved.");
        }, error -> {
            save.setEnabled(true);
            Ui.showError(this, error);
        });
    }
}
