package client.ui;

import client.controller.ClientController;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginFrame extends JFrame {
    private final ClientController controller;
    private final JTextField txtHost = new JTextField("localhost");
    private final JTextField txtPort = new JTextField("8888");
    private final JTextField txtUsername = new JTextField();
    private final JPasswordField txtPassword = new JPasswordField();
    private final JLabel lblStatus = Theme.label(" ", Theme.FONT_REGULAR, Theme.TEXT_MUTED);
    private final JButton btnLogin = Theme.button("Vào phòng chỉ huy  →", true);
    private final JCheckBox showPassword = new JCheckBox("Hiện mật khẩu");

    public LoginFrame(ClientController controller) {
        super("Battleship • Chào mừng chỉ huy");
        this.controller = controller;
        Theme.configureWindow(this, 1080, 780, 940, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel root = Theme.transparent(new GridLayout(1, 2, 36, 0));
        root.setBorder(new EmptyBorder(28, 28, 28, 28));
        setContentPane(root);
        root.setBackground(Theme.BG_DARK); root.setOpaque(true);

        JPanel hero = Theme.card(Theme.PANEL_BG, 28);
        hero.setLayout(new BorderLayout(0, 10));
        JPanel story = Theme.stack();
        Theme.addStack(story, Theme.label("BATTLESHIP  /  ONLINE", Theme.FONT_BOLD, Theme.PRIMARY), 28);
        Theme.addStack(story, Theme.label("<html>Làm chủ<br>từng hải phận.</html>", Theme.FONT_TITLE.deriveFont(39f), Theme.TEXT_MAIN), 16);

        hero.add(story, BorderLayout.NORTH);
        hero.add(new NavalArt(), BorderLayout.CENTER);
        JPanel facts = Theme.transparent(new GridLayout(1,3,10,0));
        facts.add(fact("10 × 10", "Ô chiến thuật")); facts.add(fact("05", "Tàu chiến")); facts.add(fact("15s", "Mỗi lượt bắn"));
        hero.add(facts, BorderLayout.SOUTH); root.add(hero);

        JPanel form = Theme.stack();
        Theme.addStack(form, Theme.badge("HẢI CHIẾN ĐỐI KHÁNG  1 vs 1", Theme.PRIMARY), 16);
        Theme.addStack(form, Theme.label("Đăng nhập", Theme.FONT_TITLE, Theme.TEXT_MAIN), 28);
        JPanel connection = Theme.transparent(new GridLayout(1,2,12,0));
        connection.add(loginField("Máy chủ", txtHost)); connection.add(loginField("Cổng kết nối", txtPort));
        connection.setMaximumSize(new Dimension(Integer.MAX_VALUE,72));Theme.addStack(form, connection, 18);
        Theme.addStack(form, loginField("Tên chỉ huy", txtUsername), 18);
        Theme.addStack(form, loginField("Mật khẩu", txtPassword), 12);
        showPassword.setOpaque(false);showPassword.setForeground(Theme.TEXT_MUTED);showPassword.setFont(Theme.FONT_REGULAR);
        showPassword.setBorder(BorderFactory.createEmptyBorder());showPassword.setMargin(new Insets(0,0,0,0));
        final char echo = txtPassword.getEchoChar();
        showPassword.addActionListener(e -> txtPassword.setEchoChar(showPassword.isSelected() ? (char)0 : echo));
        Theme.addStack(form, showPassword, 22);
        btnLogin.setPreferredSize(new Dimension(220,48));
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));
        btnLogin.addActionListener(e -> performLogin());Theme.addStack(form, btnLogin, 10);
        lblStatus.setPreferredSize(new Dimension(0,48));lblStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));
        lblStatus.setHorizontalAlignment(SwingConstants.LEFT);lblStatus.setVerticalAlignment(SwingConstants.TOP);
        Theme.addStack(form, lblStatus, 0);
        JPanel formArea = Theme.transparent(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.weightx=1;constraints.weighty=1;constraints.fill=GridBagConstraints.HORIZONTAL;
        constraints.insets=new Insets(0,12,0,12);formArea.add(form,constraints);
        root.add(formArea);getRootPane().setDefaultButton(btnLogin);
        SwingUtilities.invokeLater(() -> txtUsername.requestFocusInWindow());
    }
    private JPanel loginField(String title,JTextField input) {
        JPanel field=Theme.field(title,input);
        for(Component component:field.getComponents())if(component instanceof JLabel)component.setFont(Theme.FONT_REGULAR);
        input.setFont(Theme.FONT_REGULAR.deriveFont(16f));
        field.setPreferredSize(new Dimension(180,72));field.setMaximumSize(new Dimension(Integer.MAX_VALUE,72));
        return field;
    }
    private JPanel fact(String value, String title) {
        JPanel p = Theme.stack();Theme.addStack(p,Theme.label(value,Theme.FONT_HEADER,Theme.TEXT_MAIN),4);
        Theme.addStack(p,Theme.muted(title),0);return p;
    }
    private void performLogin() {
        if(!btnLogin.isEnabled())return;
        String host=txtHost.getText().trim(), username=txtUsername.getText().trim();
        int port;
        if(host.isEmpty()){showLoginError("Nhập địa chỉ máy chủ để kết nối.");txtHost.requestFocusInWindow();return;}
        try {port=Integer.parseInt(txtPort.getText().trim());if(port<1||port>65535)throw new NumberFormatException();}
        catch(NumberFormatException e){showLoginError("Cổng phải là số từ 1 đến 65535.");txtPort.requestFocusInWindow();return;}
        if(!username.matches("[A-Za-z0-9_]{1,32}")){showLoginError("Tên gồm 1–32 chữ cái, chữ số hoặc dấu gạch dưới.");txtUsername.requestFocusInWindow();return;}
        char[] password=txtPassword.getPassword();
        if(password.length==0||password.length>128){java.util.Arrays.fill(password,'\0');showLoginError("Mật khẩu cần có từ 1 đến 128 ký tự.");txtPassword.requestFocusInWindow();return;}
        setBusy(true);lblStatus.setForeground(Theme.PRIMARY);lblStatus.setText("Đang kết nối đến hạm đội...");
        controller.connectAndLogin(host,port,username,new String(password));java.util.Arrays.fill(password,'\0');
    }
    private void setBusy(boolean busy) {
        btnLogin.setEnabled(!busy);btnLogin.setText(busy?"Đang kết nối...":"Vào phòng chỉ huy  →");
        txtHost.setEnabled(!busy);txtPort.setEnabled(!busy);txtUsername.setEnabled(!busy);txtPassword.setEnabled(!busy);showPassword.setEnabled(!busy);
    }
    public void showLoginError(String error) {setBusy(false);lblStatus.setText("<html>"+Theme.html(error)+"</html>");lblStatus.setForeground(Theme.DANGER);}
    public void reset() {setBusy(false);lblStatus.setText(" ");}
}
