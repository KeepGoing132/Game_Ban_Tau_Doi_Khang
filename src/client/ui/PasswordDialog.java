package client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.function.BiConsumer;

/** Modeless so network callbacks and lobby lifecycle changes continue while it is open. */
public final class PasswordDialog extends JDialog {
    private final JPasswordField oldPassword = new JPasswordField(24);
    private final JPasswordField newPassword = new JPasswordField(24);
    private final JPasswordField confirmation = new JPasswordField(24);
    private final JButton submit = Theme.button("Lưu mật khẩu mới", true);
    private final JLabel status = Theme.muted("Mật khẩu có từ 1 đến 128 ký tự.");

    public PasswordDialog(Window owner, BiConsumer<String,String> change) {
        super(owner, "Đổi mật khẩu", ModalityType.MODELESS);
        JPanel body = Theme.stack();
        body.setOpaque(true);
        body.setBackground(Theme.BG_DARK);
        body.setBorder(new EmptyBorder(24,24,24,24));
        Theme.addStack(body, Theme.label("Đổi mật khẩu", Theme.FONT_HEADER, Theme.PRIMARY), 18);
        field(body, "Mật khẩu hiện tại", oldPassword);
        field(body, "Mật khẩu mới", newPassword);
        field(body, "Nhập lại mật khẩu mới", confirmation);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        Theme.addStack(body, status, 16);
        Theme.addStack(body, submit, 0);
        submit.addActionListener(e -> {
            char[] old = oldPassword.getPassword(), next = newPassword.getPassword(), again = confirmation.getPassword();
            try {
                if (old.length == 0 || next.length == 0 || next.length > 128 || new String(next).trim().isEmpty()) {
                    showResult("Nhập mật khẩu hiện tại và mật khẩu mới hợp lệ.", false);
                } else if (!Arrays.equals(next, again)) {
                    showResult("Hai lần nhập mật khẩu mới chưa khớp.", false);
                } else {
                    setPending(true);
                    status.setText("Đang lưu mật khẩu mới...");
                    change.accept(new String(old), new String(next));
                }
            } finally {
                Arrays.fill(old, '\0'); Arrays.fill(next, '\0'); Arrays.fill(again, '\0');
            }
        });
        setContentPane(body);
        setSize(460, 460);
        setResizable(false);
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(submit);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void field(JPanel body, String name, JPasswordField field) {
        Theme.styleField(field);
        field.getAccessibleContext().setAccessibleName(name);
        Theme.addStack(body, Theme.muted(name), 6);
        Theme.addStack(body, field, 12);
    }

    private void setPending(boolean value) {
        submit.setEnabled(!value);
        oldPassword.setEnabled(!value); newPassword.setEnabled(!value); confirmation.setEnabled(!value);
    }

    public void showResult(String message, boolean success) {
        setPending(false);
        status.setText("<html>" + Theme.html(message) + "</html>");
        status.setForeground(success ? Theme.MINT : Theme.CORAL);
        if (success) { oldPassword.setText(""); newPassword.setText(""); confirmation.setText(""); }
    }

    @Override public void dispose() {
        oldPassword.setText(""); newPassword.setText(""); confirmation.setText("");
        super.dispose();
    }
}
