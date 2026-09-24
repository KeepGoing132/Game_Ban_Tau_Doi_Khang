package client.ui;

import client.controller.ClientController;
import common.model.User;
import common.protocol.JsonUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class LobbyFrame extends JFrame {
    private final ClientController controller;

    private final JLabel lblUserInfo;
    private final JTable tblOnlineUsers;
    private final DefaultTableModel tableModel;
    private final JButton btnQuickMatch;
    private final JLabel lblQuickMatchStatus;
    private final JTextField txtRoomCode;
    private final JLabel lblCreatedRoomCode;

    private boolean inQueue = false;

    public LobbyFrame(ClientController controller) {
        super("SẢNH CHỜ HẢI CHIẾN - BATTLESHIP ONLINE");
        this.controller = controller;

        setSize(1000, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BG_DARK);
        setLayout(new BorderLayout(15, 15));

        // 1. TOP HEADER (User info & Leaderboard)
        JPanel topPanel = Theme.createCardPanel();
        topPanel.setLayout(new BorderLayout(15, 0));

        lblUserInfo = new JLabel("Đang tải thông tin...", SwingConstants.LEFT);
        lblUserInfo.setFont(Theme.FONT_HEADER);
        lblUserInfo.setForeground(Theme.TEXT_MAIN);
        topPanel.add(lblUserInfo, BorderLayout.WEST);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topActions.setOpaque(false);

        JButton btnLeaderboard = new JButton("🏆 Bảng xếp hạng");
        Theme.styleButton(btnLeaderboard, Theme.WARNING, Color.BLACK);
        btnLeaderboard.addActionListener(e -> controller.requestLeaderboard());
        topActions.add(btnLeaderboard);

        JButton btnLogout = new JButton("🚪 Đăng xuất");
        Theme.styleButton(btnLogout, Theme.PANEL_LIGHT, Theme.TEXT_MUTED);
        btnLogout.addActionListener(e -> controller.logout());
        topActions.add(btnLogout);

        topPanel.add(topActions, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // 2. CENTER: Split into Left (Online Users) and Right (Matchmaking & Rooms)
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(0, 15, 15, 15));

        // LEFT: ONLINE USERS LIST
        JPanel leftPanel = Theme.createCardPanel();
        leftPanel.setLayout(new BorderLayout(10, 10));

        JLabel lblOnlineTitle = new JLabel("🌐 DANH SÁCH NGƯỜI CHƠI ONLINE");
        lblOnlineTitle.setFont(Theme.FONT_HEADER);
        lblOnlineTitle.setForeground(Theme.PRIMARY);
        leftPanel.add(lblOnlineTitle, BorderLayout.NORTH);

        String[] cols = {"ID", "Tên người chơi", "Điểm", "Thắng", "Trạng thái"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tblOnlineUsers = new JTable(tableModel);
        tblOnlineUsers.setFont(Theme.FONT_REGULAR);
        tblOnlineUsers.setRowHeight(28);
        tblOnlineUsers.setBackground(Theme.BG_DARK);
        tblOnlineUsers.setForeground(Theme.TEXT_MAIN);
        tblOnlineUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblOnlineUsers.getTableHeader().setFont(Theme.FONT_BOLD);
        tblOnlineUsers.getTableHeader().setBackground(Theme.PANEL_LIGHT);
        tblOnlineUsers.getTableHeader().setForeground(Theme.TEXT_MAIN);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < tblOnlineUsers.getColumnCount(); i++) {
            tblOnlineUsers.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JScrollPane scrollTable = new JScrollPane(tblOnlineUsers);
        scrollTable.getViewport().setBackground(Theme.BG_DARK);
        leftPanel.add(scrollTable, BorderLayout.CENTER);

        JPanel leftBottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        leftBottom.setOpaque(false);

        JButton btnInvite = new JButton("⚔️ Thách đấu người này");
        Theme.styleDangerButton(btnInvite);
        btnInvite.addActionListener(e -> inviteSelectedUser());
        leftBottom.add(btnInvite);

        JButton btnRefresh = new JButton("🔄 Làm mới");
        Theme.styleButton(btnRefresh, Theme.PANEL_LIGHT, Theme.TEXT_MAIN);
        btnRefresh.addActionListener(e -> controller.refreshOnlineUsers());
        leftBottom.add(btnRefresh);

        leftPanel.add(leftBottom, BorderLayout.SOUTH);
        centerPanel.add(leftPanel);

        // RIGHT: MATCHMAKING & ROOM ACTIONS
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        // Card 1: Hàng chờ tự động
        JPanel cardQueue = Theme.createCardPanel();
        cardQueue.setLayout(new BoxLayout(cardQueue, BoxLayout.Y_AXIS));

        JLabel lblQueueTitle = new JLabel("⚡ GHÉP TRẬN NHANH (AUTO MATCHMAKING)");
        lblQueueTitle.setFont(Theme.FONT_HEADER);
        lblQueueTitle.setForeground(Theme.SUCCESS);
        cardQueue.add(lblQueueTitle);
        cardQueue.add(Box.createRigidArea(new Dimension(0, 10)));

        lblQuickMatchStatus = new JLabel("Nhấn để hệ thống tự động tìm đối thủ xứng tầm.");
        lblQuickMatchStatus.setFont(Theme.FONT_REGULAR);
        lblQuickMatchStatus.setForeground(Theme.TEXT_MUTED);
        cardQueue.add(lblQuickMatchStatus);
        cardQueue.add(Box.createRigidArea(new Dimension(0, 15)));

        btnQuickMatch = new JButton("🚀 BẮT ĐẦU TÌM TRẬN");
        Theme.styleSuccessButton(btnQuickMatch);
        btnQuickMatch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnQuickMatch.addActionListener(e -> toggleMatchmakingQueue());
        cardQueue.add(btnQuickMatch);

        rightPanel.add(cardQueue);
        rightPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Card 2: Tạo phòng riêng
        JPanel cardCreate = Theme.createCardPanel();
        cardCreate.setLayout(new BoxLayout(cardCreate, BoxLayout.Y_AXIS));

        JLabel lblCreateTitle = new JLabel("🏠 TẠO PHÒNG RIÊNG (CREATE ROOM)");
        lblCreateTitle.setFont(Theme.FONT_HEADER);
        lblCreateTitle.setForeground(Theme.PRIMARY);
        cardCreate.add(lblCreateTitle);
        cardCreate.add(Box.createRigidArea(new Dimension(0, 10)));

        JButton btnCreateRoom = new JButton("Tạo phòng & Lấy mã phòng");
        Theme.stylePrimaryButton(btnCreateRoom);
        btnCreateRoom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnCreateRoom.addActionListener(e -> controller.createRoom());
        cardCreate.add(btnCreateRoom);
        cardCreate.add(Box.createRigidArea(new Dimension(0, 10)));

        lblCreatedRoomCode = new JLabel("Mã phòng của bạn: Chưa tạo");
        lblCreatedRoomCode.setFont(Theme.FONT_BOLD);
        lblCreatedRoomCode.setForeground(Theme.WARNING);
        cardCreate.add(lblCreatedRoomCode);

        rightPanel.add(cardCreate);
        rightPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Card 3: Vào phòng bằng mã
        JPanel cardJoin = Theme.createCardPanel();
        cardJoin.setLayout(new BoxLayout(cardJoin, BoxLayout.Y_AXIS));

        JLabel lblJoinTitle = new JLabel("🔑 VÀO PHÒNG BẰNG MÃ (JOIN ROOM)");
        lblJoinTitle.setFont(Theme.FONT_HEADER);
        lblJoinTitle.setForeground(Theme.TEXT_MAIN);
        cardJoin.add(lblJoinTitle);
        cardJoin.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel joinInputPanel = new JPanel(new BorderLayout(10, 0));
        joinInputPanel.setOpaque(false);
        txtRoomCode = new JTextField();
        txtRoomCode.setFont(Theme.FONT_HEADER);
        txtRoomCode.setBackground(Theme.BG_DARK);
        txtRoomCode.setForeground(Theme.TEXT_MAIN);
        txtRoomCode.setCaretColor(Color.WHITE);
        joinInputPanel.add(txtRoomCode, BorderLayout.CENTER);

        JButton btnJoin = new JButton("Tham gia");
        Theme.styleButton(btnJoin, Theme.PANEL_LIGHT, Theme.TEXT_MAIN);
        btnJoin.addActionListener(e -> {
            String code = txtRoomCode.getText().trim();
            if (!code.isEmpty()) {
                controller.joinRoom(code);
            }
        });
        joinInputPanel.add(btnJoin, BorderLayout.EAST);
        cardJoin.add(joinInputPanel);

        rightPanel.add(cardJoin);
        centerPanel.add(rightPanel);

        add(centerPanel, BorderLayout.CENTER);
    }

    public void updateUserInfo(User u) {
        if (u == null) return;
        lblUserInfo.setText(String.format("👤 %s | 🏆 %d Điểm | 🥇 %d Thắng / %d Trận (Tỷ lệ: %.1f%%)",
                u.getUsername(), u.getTotalPoints(), u.getTotalWins(), u.getTotalGames(), u.getWinRate()));
    }

    public void updateOnlineUsers(List<Object> usersList) {
        tableModel.setRowCount(0);
        if (usersList == null) return;

        for (Object item : usersList) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                int id = JsonUtil.getInt(map, "user_id", 0);
                String name = JsonUtil.getString(map, "username", "");
                int pts = JsonUtil.getInt(map, "total_points", 0);
                int wins = JsonUtil.getInt(map, "total_wins", 0);
                String status = JsonUtil.getString(map, "status", "IDLE");
                String statusDisplay = "IDLE".equals(status) ? "🟢 Rảnh" : ("BUSY".equals(status) ? "🔴 Đang đấu" : "⚪ Offline");

                tableModel.addRow(new Object[]{id, name, pts, wins, statusDisplay});
            }
        }
    }

    public void setCreatedRoomCode(String code) {
        lblCreatedRoomCode.setText("Mã phòng của bạn: " + code + " (Đang chờ đối thủ...)");
    }

    private void toggleMatchmakingQueue() {
        if (!inQueue) {
            inQueue = true;
            btnQuickMatch.setText("❌ HỦY TÌM TRẬN");
            Theme.styleButton(btnQuickMatch, Theme.DANGER, Color.WHITE);
            lblQuickMatchStatus.setText("Đang tìm đối thủ trong hàng đợi...");
            controller.joinQueue();
        } else {
            inQueue = false;
            btnQuickMatch.setText("🚀 BẮT ĐẦU TÌM TRẬN");
            Theme.styleSuccessButton(btnQuickMatch);
            lblQuickMatchStatus.setText("Nhấn để hệ thống tự động tìm đối thủ xứng tầm.");
            controller.leaveQueue();
        }
    }

    public void resetQueueButton() {
        inQueue = false;
        btnQuickMatch.setText("🚀 BẮT ĐẦU TÌM TRẬN");
        Theme.styleSuccessButton(btnQuickMatch);
        lblQuickMatchStatus.setText("Nhấn để hệ thống tự động tìm đối thủ xứng tầm.");
    }

    private void inviteSelectedUser() {
        int selectedRow = tblOnlineUsers.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người chơi trong danh sách!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int targetId = (int) tableModel.getValueAt(selectedRow, 0);
        String targetName = (String) tableModel.getValueAt(selectedRow, 1);
        String targetStatus = (String) tableModel.getValueAt(selectedRow, 4);

        if (!targetStatus.contains("Rảnh")) {
            JOptionPane.showMessageDialog(this, "Người chơi này đang bận!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        controller.sendInvite(targetId);
        JOptionPane.showMessageDialog(this, "Đã gửi lời mời thách đấu tới " + targetName + "!", "Đang mời", JOptionPane.INFORMATION_MESSAGE);
    }
}
