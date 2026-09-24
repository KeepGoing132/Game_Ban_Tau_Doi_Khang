package client.ui;

import client.controller.ClientController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginFrame extends JFrame {
    private final ClientController controller;

    private final JTextField txtHost;
    private final JTextField txtPort;
    private final JTextField txtUsername;
    private final JPasswordField txtPassword;
    private final JLabel lblStatus;
    private final JButton btnLogin;

    public LoginFrame(ClientController controller) {
        super("ĐĂNG NHẬP - BATTLESHIP ONLINE");
        this.controller = controller;

        setSize(460, 560);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BG_DARK);
        setLayout(new BorderLayout());

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(new EmptyBorder(30, 40, 30, 40));

        // Header Title
        JLabel lblTitle = new JLabel("⚓ BATTLESHIP ONLINE");
        lblTitle.setFont(Theme.FONT_TITLE);
        lblTitle.setForeground(Theme.PRIMARY);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(lblTitle);

        JLabel lblSub = new JLabel("Game Bắn Tàu Đối Kháng Mạng TCP");
        lblSub.setFont(Theme.FONT_SMALL);
        lblSub.setForeground(Theme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(lblSub);
        container.add(Box.createRigidArea(new Dimension(0, 25)));

        // Form Fields
        JPanel formCard = Theme.createCardPanel();
        formCard.setLayout(new GridLayout(4, 2, 10, 15));

        JLabel l1 = new JLabel("Máy chủ (Host):");
        l1.setFont(Theme.FONT_BOLD);
        l1.setForeground(Theme.TEXT_MAIN);
        txtHost = new JTextField("localhost");
        txtHost.setBackground(Theme.BG_DARK);
        txtHost.setForeground(Theme.TEXT_MAIN);

        JLabel l2 = new JLabel("Cổng (Port):");
        l2.setFont(Theme.FONT_BOLD);
        l2.setForeground(Theme.TEXT_MAIN);
        txtPort = new JTextField("8888");
        txtPort.setBackground(Theme.BG_DARK);
        txtPort.setForeground(Theme.TEXT_MAIN);

        JLabel l3 = new JLabel("Tên tài khoản:");
        l3.setFont(Theme.FONT_BOLD);
        l3.setForeground(Theme.TEXT_MAIN);
        txtUsername = new JTextField("tuan_duong");
        txtUsername.setBackground(Theme.BG_DARK);
        txtUsername.setForeground(Theme.TEXT_MAIN);

        JLabel l4 = new JLabel("Mật khẩu:");
        l4.setFont(Theme.FONT_BOLD);
        l4.setForeground(Theme.TEXT_MAIN);
        txtPassword = new JPasswordField("123456");
        txtPassword.setBackground(Theme.BG_DARK);
        txtPassword.setForeground(Theme.TEXT_MAIN);

        formCard.add(l1); formCard.add(txtHost);
        formCard.add(l2); formCard.add(txtPort);
        formCard.add(l3); formCard.add(txtUsername);
        formCard.add(l4); formCard.add(txtPassword);
        container.add(formCard);
        container.add(Box.createRigidArea(new Dimension(0, 15)));

        // Quick sample account selector
        JLabel lblQuick = new JLabel("Chọn nhanh tài khoản mẫu để test:");
        lblQuick.setFont(Theme.FONT_SMALL);
        lblQuick.setForeground(Theme.TEXT_MUTED);
        lblQuick.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(lblQuick);
        container.add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel samplePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
        samplePanel.setOpaque(false);
        String[] samples = {"tuan_duong", "ngoc_bao", "thanh_hai", "dang_khoa", "player1", "player2"};
        for (String sample : samples) {
            JButton b = new JButton(sample);
            Theme.styleButton(b, Theme.PANEL_LIGHT, Theme.TEXT_MAIN);
            b.setFont(Theme.FONT_SMALL);
            b.addActionListener(e -> txtUsername.setText(sample));
            samplePanel.add(b);
        }
        container.add(samplePanel);
        container.add(Box.createRigidArea(new Dimension(0, 15)));

        // Login Button
        btnLogin = new JButton("ĐĂNG NHẬP VÀO GAME");
        Theme.stylePrimaryButton(btnLogin);
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogin.addActionListener(e -> performLogin());
        container.add(btnLogin);
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        lblStatus = new JLabel(" ", SwingConstants.CENTER);
        lblStatus.setFont(Theme.FONT_REGULAR);
        lblStatus.setForeground(Theme.WARNING);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(lblStatus);

        add(container, BorderLayout.CENTER);
    }

    private void performLogin() {
        String host = txtHost.getText().trim();
        int port = 8888;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (username.isEmpty()) {
            lblStatus.setText("Vui lòng nhập tên tài khoản!");
            lblStatus.setForeground(Theme.DANGER);
            return;
        }

        lblStatus.setText("Đang kết nối tới máy chủ...");
        lblStatus.setForeground(Theme.WARNING);
        btnLogin.setEnabled(false);

        controller.connectAndLogin(host, port, username, password);
    }

    public void showLoginError(String error) {
        lblStatus.setText(error);
        lblStatus.setForeground(Theme.DANGER);
        btnLogin.setEnabled(true);
    }

    public void reset() {
        lblStatus.setText(" ");
        btnLogin.setEnabled(true);
    }
}
