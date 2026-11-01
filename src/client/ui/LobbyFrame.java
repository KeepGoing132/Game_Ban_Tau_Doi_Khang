package client.ui;

import client.controller.ClientController;
import common.model.User;
import common.protocol.JsonUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Command room: profile, online commanders and the four ways into a match. */
public class LobbyFrame extends JFrame {
    private final ClientController controller;
    private final JLabel lblUserInfo = Theme.label("Chào mừng, chỉ huy", Theme.FONT_TITLE, Theme.TEXT_MAIN);
    private final JLabel lblPoints = Theme.label("0", Theme.FONT_TITLE, Theme.TEXT_MAIN);
    private final JLabel lblWins = Theme.label("0", Theme.FONT_TITLE, Theme.TEXT_MAIN);
    private final JLabel lblRate = Theme.label("0%", Theme.FONT_TITLE, Theme.TEXT_MAIN);
    private final JLabel lblOnlineCount = Theme.muted("Đang cập nhật hạm đội...");
    private final JLabel lblFeedback = Theme.muted("Chọn ghép trận nhanh hoặc mời một người bạn.");
    private final JLabel lblPenalty = Theme.badge(" ", Theme.WARNING);
    private final JLabel lblConnection = Theme.muted("Đang đo kết nối...");
    private final JLabel lblQuickMatchStatus = Theme.label("Sẵn sàng cho một cuộc đấu trí?", Theme.FONT_REGULAR, Theme.TEXT_MUTED);
    private final JLabel lblCreatedRoomCode = Theme.label("— — — — — —", Theme.FONT_GRID.deriveFont(Font.BOLD, 29f), Theme.PRIMARY);
    private final JLabel lblRoomHint = Theme.muted("Tạo mã riêng và gửi cho bạn bè.");
    private final JTextField txtRoomCode = new JTextField();
    private final JButton btnBot = Theme.button("Đấu máy", false);
    private final JButton btnAccount = Theme.button("Tài khoản", false);
    private final JButton btnQuickMatch = Theme.button("Tìm đối thủ  →", true);
    private final JButton btnCreateRoom = Theme.button("Tạo phòng riêng", false);
    private final JButton btnJoin = Theme.button("Vào phòng", true);
    private final JButton btnInvite = Theme.button("Mời thách đấu", false);
    private final JButton btnChat = Theme.button("Nhắn tin", true);
    private final JButton btnHistory = Theme.button("Lịch sử", false);
    private final JButton btnLeaveRoom = Theme.button("Rời phòng", false);
    private final JButton btnCopy = Theme.button("Sao chép mã", false);
    private final JTable tblOnlineUsers;
    private final DefaultTableModel tableModel;
    private final Timer queueTimer;
    private final Timer penaltyTimer;
    private boolean inQueue;
    private boolean pending;
    private String phase = "IDLE";
    private String roomCode;
    private int currentUserId = -1;
    private long queueStarted;
    private long penaltyDeadlineNanos;
    private boolean penaltyPending;

    public LobbyFrame(ClientController controller) {
        super("Battleship • Phòng chỉ huy");
        this.controller = controller;
        Theme.configureWindow(this, 1320, 900, 1120, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = Theme.transparent(new BorderLayout());
        root.setOpaque(true);
        root.setBackground(Theme.BG_DARK);
        setContentPane(root);
        root.add(sidebar(), BorderLayout.WEST);

        JPanel main = Theme.transparent(new BorderLayout(0, 16));
        main.setBorder(new EmptyBorder(24, 28, 22, 28));
        root.add(main, BorderLayout.CENTER);
        main.add(profileHeader(), BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"ID", "Chỉ huy", "Điểm", "Trạng thái"}, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblOnlineUsers = new JTable(tableModel);

        JPanel body = Theme.transparent(new BorderLayout(18, 0));
        main.add(Theme.scrollPage(body, 466, Theme.BG_DARK), BorderLayout.CENTER);
        JPanel left = Theme.transparent(new BorderLayout(0, 18));
        body.add(left, BorderLayout.CENTER);
        left.add(matchmakingCard(), BorderLayout.NORTH);
        left.add(onlineCard(), BorderLayout.CENTER);
        JPanel right = Theme.stack();
        right.setPreferredSize(new Dimension(286, 0));
        body.add(right, BorderLayout.EAST);
        Theme.addStack(right, roomCard(), 16);
        Theme.addStack(right, joinCard(), 0);

        main.add(notices(), BorderLayout.SOUTH);
        queueTimer = new Timer(1000, e -> updateQueueText());
        penaltyTimer = new Timer(250, e -> updatePenalty());
        updateActions();
    }

    // ------------------------------------------------------------------ layout

    private JPanel sidebar() {
        JPanel sidebar = Theme.stack();
        sidebar.setOpaque(true);
        sidebar.setBackground(Theme.PANEL_BG);
        sidebar.setPreferredSize(new Dimension(185, 0));
        sidebar.setBorder(new EmptyBorder(30, 20, 24, 20));
        Theme.addStack(sidebar, Theme.label("BATTLESHIP", Theme.FONT_HEADER, Theme.PRIMARY), 4);
        Theme.addStack(sidebar, Theme.badge("HẢI CHIẾN", Theme.VIOLET), 32);
        Theme.addStack(sidebar, navButton("01   Phòng chỉ huy", Theme.PRIMARY,
                e -> showStatus("Bạn đang ở phòng chỉ huy. Chọn một chế độ để bắt đầu.", false)), 8);
        Theme.addStack(sidebar, navButton("02   Bảng xếp hạng", Theme.GOLD,
                e -> controller.requestLeaderboard()), 8);
        Theme.addStack(sidebar, navButton("03   Lịch sử đấu", Theme.MAGENTA,
                e -> controller.requestMatchHistory(0)), 8);
        Theme.addStack(sidebar, navButton("04   Cách chơi", Theme.AZURE, e -> showGuide()), 8);
        Theme.addStack(sidebar, navButton("05   Cài đặt", Theme.LIME, e -> SettingsDialog.open(this)), 8);
        sidebar.add(Box.createVerticalGlue());
        btnAccount.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnAccount.addActionListener(e -> accountDialog());
        Theme.addStack(sidebar, btnAccount, 8);
        Theme.addStack(sidebar, lblConnection, 16);
        JButton logout = Theme.button("Đăng xuất", false);
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        logout.addActionListener(e -> controller.logout());
        Theme.addStack(sidebar, logout, 0);
        return sidebar;
    }

    private JButton navButton(String text, Color accent, java.awt.event.ActionListener action) {
        JButton button = Theme.button(text, false);
        button.setForeground(accent);
        button.setFont(Theme.FONT_SMALL);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.addActionListener(action);
        return button;
    }

    private JPanel profileHeader() {
        JPanel top = Theme.stack();
        Theme.addStack(top, lblUserInfo, 5);
        top.add(Box.createVerticalStrut(12));
        JPanel stats = Theme.transparent(new GridLayout(1, 3, 14, 0));
        stats.add(stat("TỔNG ĐIỂM", lblPoints, "+3 điểm cho mỗi chiến thắng", Theme.AMBER));
        stats.add(stat("TRẬN THẮNG", lblWins, "Số trận thắng / đã chơi", Theme.MINT));
        stats.add(stat("TỶ LỆ THẮNG", lblRate, "", Theme.AZURE));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94));
        Theme.addStack(top, stats, 0);
        return top;
    }

    private JPanel stat(String title, JLabel value, String detail, Color accent) {
        JPanel card = Theme.vividCard(Theme.blend(new Color(22, 26, 64), accent, .26f),
                Theme.blend(new Color(30, 44, 84), accent, .16f), 15);
        card.setLayout(new BorderLayout(0, 4));
        card.add(Theme.label(title, Theme.FONT_SMALL.deriveFont(Font.BOLD), accent), BorderLayout.NORTH);
        value.setForeground(accent);
        card.add(value, BorderLayout.CENTER);
        card.add(Theme.muted(detail), BorderLayout.SOUTH);
        return card;
    }

    /** Quick match and practice, the two ways into a match without a code. */
    private JPanel matchmakingCard() {
        JPanel queue = Theme.vividCard(new Color(21, 84, 116), new Color(74, 58, 140), 22);
        queue.setLayout(new BorderLayout(12, 12));
        JPanel text = Theme.stack();
        Theme.addStack(text, Theme.badge("CHỌN ĐỐI THỦ", Theme.MINT), 12);
        Theme.addStack(text, Theme.label("Sẵn sàng khai hỏa?", Theme.FONT_TITLE.deriveFont(25f), Theme.SEA_FOAM), 7);
        Theme.addStack(text, lblQuickMatchStatus, 16);
        btnQuickMatch.addActionListener(e -> toggleMatchmakingQueue());
        btnBot.addActionListener(e -> botDialog());
        Theme.styleButton(btnQuickMatch, Theme.MINT, Theme.BG_DARK);
        Theme.styleButton(btnBot, Theme.ORCHID, Theme.BG_DARK);
        JPanel modes = Theme.transparent(new GridLayout(1, 2, 12, 0));
        modes.add(btnQuickMatch);
        modes.add(btnBot);
        modes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        Theme.addStack(text, modes, 0);
        queue.add(text, BorderLayout.CENTER);
        return queue;
    }

    /** Online list with the per-commander actions: invite, chat and history. */
    private JPanel onlineCard() {
        JPanel online = Theme.createCardPanel();
        online.setLayout(new BorderLayout(0, 10));

        JPanel header = Theme.transparent(new BorderLayout());
        header.add(Theme.label("Chỉ huy trực tuyến", Theme.FONT_HEADER, Theme.TEXT_MAIN), BorderLayout.WEST);
        JButton refresh = Theme.button("Làm mới", false);
        refresh.setFont(Theme.FONT_SMALL);
        refresh.setBorder(new EmptyBorder(6, 10, 6, 10));
        refresh.addActionListener(e -> controller.refreshOnlineUsers());
        header.add(refresh, BorderLayout.EAST);
        JPanel titles = Theme.stack();
        Theme.addStack(titles, header, 4);
        Theme.addStack(titles, lblOnlineCount, 0);
        online.add(titles, BorderLayout.NORTH);

        Theme.styleTable(tblOnlineUsers);
        tblOnlineUsers.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                // the ID column is hidden in the view, so status lives at view column 2
                String status = String.valueOf(table.getValueAt(row, 2));
                Color color = column == 0 ? Theme.SEA_FOAM
                        : column == 1 ? Theme.AMBER
                        : status.startsWith("●") ? Theme.MINT
                        : status.startsWith("Đang bị") ? Theme.CORAL
                        : Theme.WARNING;
                setForeground(selected ? Theme.PRIMARY : color);
                setFont(column == 1 ? Theme.FONT_BOLD : Theme.FONT_REGULAR);
                setBackground(selected ? Theme.PANEL_LIGHT : (row % 2 == 0 ? Theme.PANEL_BG : Theme.blend(Theme.PANEL_BG, Theme.PANEL_LIGHT, .35f)));
                setBorder(new EmptyBorder(0, 12, 0, 12));
                return this;
            }
        });
        tblOnlineUsers.removeColumn(tblOnlineUsers.getColumnModel().getColumn(0));
        tblOnlineUsers.getColumnModel().getColumn(0).setPreferredWidth(160);
        tblOnlineUsers.getColumnModel().getColumn(1).setPreferredWidth(60);
        tblOnlineUsers.getColumnModel().getColumn(2).setPreferredWidth(100);
        tblOnlineUsers.getSelectionModel().addListSelectionListener(e -> updateActions());
        online.add(Theme.scroll(tblOnlineUsers), BorderLayout.CENTER);

        btnInvite.addActionListener(e -> inviteSelectedUser());
        Theme.styleButton(btnHistory, Theme.MINT, Theme.BG_DARK);
        btnHistory.addActionListener(e -> {
            Integer id = selectedUserId();
            if (id != null) {
                controller.requestMatchHistory(id);
            }
        });
        btnChat.addActionListener(e -> {
            int row = tblOnlineUsers.getSelectedRow();
            if (row < 0) {
                return;
            }
            int model = tblOnlineUsers.convertRowIndexToModel(row);
            controller.openChat((Integer) tableModel.getValueAt(model, 0), (String) tableModel.getValueAt(model, 1));
        });
        JPanel playerActions = Theme.transparent(new GridLayout(1, 3, 10, 0));
        playerActions.add(btnInvite);
        playerActions.add(btnChat);
        playerActions.add(btnHistory);
        online.add(playerActions, BorderLayout.SOUTH);
        return online;
    }

    private JPanel roomCard() {
        JPanel room = Theme.createCardPanel();
        room.setLayout(new BoxLayout(room, BoxLayout.Y_AXIS));
        Theme.addStack(room, Theme.label("Chơi cùng bạn bè", Theme.FONT_HEADER, Theme.TEXT_MAIN), 8);
        Theme.addStack(room, lblRoomHint, 20);
        Theme.addStack(room, lblCreatedRoomCode, 18);
        btnCreateRoom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnCreateRoom.addActionListener(e -> {
            beginAction();
            controller.createRoom();
        });
        Theme.addStack(room, btnCreateRoom, 8);
        btnCopy.setFont(Theme.FONT_SMALL);
        btnCopy.setBorder(new EmptyBorder(9, 8, 9, 8));
        btnCopy.addActionListener(e -> copyRoomCode());
        btnLeaveRoom.setFont(Theme.FONT_SMALL);
        btnLeaveRoom.setBorder(new EmptyBorder(9, 8, 9, 8));
        btnLeaveRoom.addActionListener(e -> {
            beginAction();
            controller.leaveRoom();
        });
        JPanel actions = Theme.transparent(new GridLayout(1, 2, 8, 0));
        actions.add(btnCopy);
        actions.add(btnLeaveRoom);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        Theme.addStack(room, actions, 0);
        room.setMaximumSize(new Dimension(Integer.MAX_VALUE, 244));
        return room;
    }

    private JPanel joinCard() {
        JPanel join = Theme.createCardPanel();
        join.setLayout(new BoxLayout(join, BoxLayout.Y_AXIS));
        Theme.addStack(join, Theme.label("Đã có mã phòng?", Theme.FONT_HEADER, Theme.TEXT_MAIN), 8);
        Theme.addStack(join, Theme.muted("Nhập 6 ký tự từ người tạo phòng."), 14);
        Theme.styleField(txtRoomCode);
        txtRoomCode.setFont(Theme.FONT_GRID.deriveFont(Font.BOLD, 21f));
        txtRoomCode.setHorizontalAlignment(SwingConstants.CENTER);
        txtRoomCode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        txtRoomCode.getAccessibleContext().setAccessibleName("Mã phòng gồm sáu ký tự");
        ((AbstractDocument) txtRoomCode.getDocument()).setDocumentFilter(new DocumentFilter() {
            public void insertString(FilterBypass fb, int offset, String value, AttributeSet attributes) throws BadLocationException {
                replace(fb, offset, 0, value, attributes);
            }

            public void replace(FilterBypass fb, int offset, int length, String value, AttributeSet attributes) throws BadLocationException {
                String clean = value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
                int room = 6 - fb.getDocument().getLength() + length;
                if (clean.length() > room) {
                    clean = clean.substring(0, Math.max(0, room));
                }
                super.replace(fb, offset, length, clean, attributes);
            }
        });
        Theme.addStack(join, txtRoomCode, 10);
        btnJoin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnJoin.addActionListener(e -> joinRoom());
        txtRoomCode.addActionListener(e -> {
            if (btnJoin.isEnabled()) {
                joinRoom();
            }
        });
        Theme.addStack(join, btnJoin, 0);
        join.setMaximumSize(new Dimension(Integer.MAX_VALUE, 206));
        return join;
    }

    private JPanel notices() {
        JPanel notices = Theme.stack();
        lblPenalty.setVisible(false);
        Theme.addStack(notices, lblPenalty, 0);
        lblFeedback.setBorder(new EmptyBorder(6, 0, 0, 0));
        Theme.addStack(notices, lblFeedback, 0);
        return notices;
    }

    private void copyRoomCode() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(roomCode), null);
            showStatus("Đã sao chép mã phòng " + roomCode + ".", false);
        } catch (IllegalStateException e) {
            showStatus("Chưa thể sao chép. Mã phòng: " + roomCode, true);
        }
    }

    // ------------------------------------------------------------------ state

    public void updateUserInfo(User user) {
        if (user == null) {
            return;
        }
        currentUserId = user.getUserId();
        lblUserInfo.setText("Chào mừng, " + user.getUsername());
        lblPoints.setText(String.valueOf(user.getTotalPoints()));
        lblWins.setText(user.getTotalWins() + " / " + user.getTotalGames());
        lblRate.setText(String.format(Locale.ROOT, "%.0f%%", user.getWinRate()));
        updateActions();
    }

    public void updateOnlineUsers(List<Object> users) {
        Integer selected = selectedUserId();
        tableModel.setRowCount(0);
        int others = 0;
        if (users != null) {
            for (Object item : users) {
                Map<String, Object> user = asMap(item);
                if (user == null) {
                    continue;
                }
                int id = JsonUtil.getInt(user, "user_id", 0);
                if (id == currentUserId) {
                    continue;
                }
                String status = JsonUtil.getString(user, "status", "IDLE");
                boolean blocked = JsonUtil.getLong(user, "matchmaking_wait_ms", 0) > 0
                        || JsonUtil.getBoolean(user, "penalty_pending", false);
                tableModel.addRow(new Object[]{id, JsonUtil.getString(user, "username", ""),
                        JsonUtil.getInt(user, "total_points", 0),
                        blocked ? "Đang bị phạt" : "IDLE".equals(status) ? "● Sẵn sàng" : "Đang bận"});
                others++;
                if (selected != null && selected == id) {
                    tblOnlineUsers.setRowSelectionInterval(tableModel.getRowCount() - 1, tableModel.getRowCount() - 1);
                }
            }
        }
        lblOnlineCount.setText(others == 0 ? "Chưa có đối thủ. Mời bạn bè cùng kết nối."
                : others + " người chơi • Chọn một chỉ huy để thách đấu");
        updateActions();
    }

    private Integer selectedUserId() {
        int row = tblOnlineUsers.getSelectedRow();
        return row < 0 ? null : (Integer) tableModel.getValueAt(tblOnlineUsers.convertRowIndexToModel(row), 0);
    }

    private void updateActions() {
        boolean idle = "IDLE".equals(phase) && !pending && !isPenalized();
        btnBot.setEnabled(idle);
        btnAccount.setEnabled(!pending && ("IDLE".equals(phase) || "COOLDOWN".equals(phase)));
        btnCreateRoom.setEnabled(idle);
        btnJoin.setEnabled(idle);
        txtRoomCode.setEnabled(idle);
        int row = tblOnlineUsers == null ? -1 : tblOnlineUsers.getSelectedRow();
        boolean available = row >= 0 && "● Sẵn sàng".equals(tableModel.getValueAt(tblOnlineUsers.convertRowIndexToModel(row), 3));
        btnChat.setEnabled(row >= 0);
        btnHistory.setEnabled(row >= 0);
        btnInvite.setEnabled(idle && available);
        btnQuickMatch.setEnabled(!pending && !isPenalized() && (idle || inQueue));
        btnCopy.setEnabled(roomCode != null);
        btnLeaveRoom.setEnabled(!pending && roomCode != null);
    }

    private boolean isPenalized() {
        return penaltyPending || (penaltyDeadlineNanos != 0 && penaltyDeadlineNanos - System.nanoTime() > 0);
    }

    /** The server owns the deadline; the lobby only counts it down. */
    public void setPenalty(long remainingMs, boolean awaitingSave) {
        penaltyDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, remainingMs));
        penaltyPending = awaitingSave;
        updatePenalty();
        if (isPenalized()) {
            penaltyTimer.start();
        }
    }

    private void updatePenalty() {
        boolean wasVisible = lblPenalty.isVisible();
        boolean blocked = isPenalized();
        lblPenalty.setVisible(blocked);
        if (blocked) {
            long seconds = Math.max(0, (penaltyDeadlineNanos - System.nanoTime() + 999999999L) / 1000000000L);
            String time = String.format("%02d:%02d", seconds / 60, seconds % 60);
            lblPenalty.setText(seconds > 0
                    ? "Phạt rời trận: tạm khóa ghép trận • Còn " + time
                    : "Đang lưu kết quả rời trận. Chưa thể ghép trận mới.");
            lblQuickMatchStatus.setForeground(Theme.WARNING);
            lblQuickMatchStatus.setText("Rời trận: trừ tối đa 3 điểm và chờ 1 phút.");
            btnQuickMatch.setText(seconds > 0 ? "Ghép trận sau " + time : "Đang lưu hình phạt...");
        } else {
            penaltyTimer.stop();
            if ("COOLDOWN".equals(phase)) {
                phase = "IDLE";
            }
            lblQuickMatchStatus.setForeground(Theme.TEXT_MUTED);
            updateQueueText();
            btnQuickMatch.setText(inQueue ? "Hủy tìm trận" : "Tìm đối thủ  →");
            if (wasVisible) {
                showStatus("Đã hết thời gian phạt. Bạn có thể ghép trận trở lại.", false);
            }
        }
        updateActions();
        if (wasVisible != blocked) {
            revalidate();
            repaint();
        }
    }

    public void setLifecycle(String value) {
        phase = value;
        updateActions();
    }

    private void beginAction() {
        pending = true;
        updateActions();
        showStatus("Đang chờ máy chủ xác nhận...", false);
    }

    public void completeAction() {
        pending = false;
        updateActions();
    }

    public void showStatus(String text, boolean error) {
        lblFeedback.setText(text);
        lblFeedback.setForeground(error ? Theme.DANGER : Theme.TEXT_MUTED);
        lblFeedback.setToolTipText(text);
    }

    public void clearRoom() {
        roomCode = null;
        lblCreatedRoomCode.setText("— — — — — —");
        lblRoomHint.setText("Tạo mã riêng và gửi cho bạn bè.");
        phase = "IDLE";
        pending = false;
        updateActions();
    }

    public void setCreatedRoomCode(String code) {
        roomCode = code;
        lblCreatedRoomCode.setText(code);
        lblRoomHint.setText("Đang chờ người bạn cùng ra khơi...");
        phase = "IN_ROOM";
        pending = false;
        showStatus("Phòng đã sẵn sàng. Chia sẻ mã " + code + " để mời bạn bè.", false);
        updateActions();
    }

    public void setQueueState(boolean queued) {
        inQueue = queued;
        pending = false;
        phase = queued ? "QUEUED" : "IDLE";
        btnQuickMatch.setText(queued ? "Hủy tìm trận" : "Tìm đối thủ  →");
        if (queued) {
            queueStarted = System.nanoTime();
            queueTimer.start();
        } else {
            queueTimer.stop();
        }
        updateQueueText();
        showStatus(queued ? "Đang ghép trận. Bạn có thể hủy bất cứ lúc nào."
                : "Chọn chế độ chơi để bắt đầu một trận mới.", false);
        updateActions();
    }

    private void updateQueueText() {
        if (isPenalized()) {
            return;
        }
        long seconds = (System.nanoTime() - queueStarted) / 1000000000L;
        lblQuickMatchStatus.setText(inQueue
                ? String.format("Đang tìm đối thủ  •  %02d:%02d", seconds / 60, seconds % 60)
                : "Sẵn sàng cho một cuộc đấu trí?");
    }

    private void toggleMatchmakingQueue() {
        beginAction();
        if (inQueue) {
            controller.leaveQueue();
        } else {
            controller.joinQueue();
        }
    }

    public void resetQueueButton() {
        setQueueState(false);
    }

    private void joinRoom() {
        String code = txtRoomCode.getText().trim();
        if (code.length() != 6) {
            showStatus("Mã phòng cần đủ 6 ký tự.", true);
            txtRoomCode.requestFocusInWindow();
            return;
        }
        beginAction();
        controller.joinRoom(code);
    }

    private void inviteSelectedUser() {
        Integer id = selectedUserId();
        if (id == null || !btnInvite.isEnabled()) {
            return;
        }
        controller.sendInvite(id);
        showStatus("Đã gửi lời mời. Đang chờ đối thủ trả lời...", false);
    }

    /** Connection state measured by the heartbeat round trip. */
    public void setConnectionStatus(String value) {
        lblConnection.setText("<html>" + Theme.html(value) + "</html>");
    }

    // ------------------------------------------------------------------ dialogs

    private void botDialog() {
        JDialog dialog = new JDialog(this, "Đấu máy", true);
        JPanel body = Theme.stack();
        body.setOpaque(true);
        body.setBackground(Theme.BG_DARK);
        body.setBorder(new EmptyBorder(24, 24, 24, 24));
        Theme.addStack(body, Theme.label("Chọn độ khó", Theme.FONT_TITLE, Theme.TEXT_MAIN), 12);
        Theme.addStack(body, Theme.label("Luyện tập • Không tính điểm hoặc phạt rời trận", Theme.FONT_REGULAR, Theme.TEXT_MUTED), 22);
        String[] levels = {"EASY", "MEDIUM", "HARD"};
        String[] names = {"Dễ — Làm quen", "Vừa — Thử sức", "Khó — Thách thức"};
        String[] details = {"Bắn ngẫu nhiên.", "Săn ô xen kẽ, truy tìm quanh điểm trúng.", "Tính xác suất, ưu tiên hướng tàu còn lại."};
        Color[] colors = {Theme.PRIMARY, Theme.BLUE, Theme.VIOLET};
        for (int i = 0; i < levels.length; i++) {
            final String level = levels[i];
            JButton choice = Theme.button(names[i], true);
            Theme.styleButton(choice, colors[i], Theme.BG_DARK);
            choice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            choice.addActionListener(e -> {
                dialog.dispose();
                beginAction();
                controller.startBot(level);
            });
            Theme.addStack(body, choice, 6);
            Theme.addStack(body, Theme.label(details[i], Theme.FONT_REGULAR, Theme.TEXT_MUTED), 18);
        }
        dialog.setContentPane(body);
        dialog.setSize(490, 445);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void accountDialog() {
        Object[] actions = {"Đổi mật khẩu", "Xóa tài khoản", "Đóng"};
        int choice = JOptionPane.showOptionDialog(this, "Quản lý tài khoản đang đăng nhập", "Tài khoản",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
        if (choice == 0) {
            controller.openPasswordDialog();
        } else if (choice == 1) {
            deleteAccountDialog();
        }
    }

    private void deleteAccountDialog() {
        JPanel body = Theme.stack();
        Theme.addStack(body, Theme.label("Xóa tài khoản của bạn?", Theme.FONT_HEADER, Theme.DANGER), 12);
        Theme.addStack(body, Theme.label("<html>Tên đăng nhập và mật khẩu sẽ bị xóa khỏi database.<br>Điểm không thể khôi phục. Lịch sử trận được ẩn danh.</html>", Theme.FONT_REGULAR, Theme.TEXT_MAIN), 16);
        JPasswordField password = new JPasswordField(22);
        Theme.styleField(password);
        password.getAccessibleContext().setAccessibleName("Mật khẩu xác nhận xóa tài khoản");
        Theme.addStack(body, Theme.label("Nhập mật khẩu để xác nhận", Theme.FONT_REGULAR, Theme.TEXT_MUTED), 6);
        Theme.addStack(body, password, 0);
        int choice = JOptionPane.showOptionDialog(this, body, "Xóa tài khoản", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, new Object[]{"Xóa tài khoản", "Hủy"}, "Hủy");
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        char[] chars = password.getPassword();
        if (chars.length == 0) {
            showStatus("Cần nhập mật khẩu để xóa tài khoản.", true);
            return;
        }
        beginAction();
        controller.deleteAccount(new String(chars));
        java.util.Arrays.fill(chars, '\0');
    }

    private void showGuide() {
        JOptionPane.showMessageDialog(this,
                "1. Tìm đối thủ hoặc tạo phòng để chơi cùng bạn bè.\n"
                        + "2. Đặt 5 tàu (5, 4, 3, 3, 2 ô), rồi nhấn Sẵn sàng.\n"
                        + "3. Chọn tọa độ trên bản đồ đối thủ, rồi nhấn Khai hỏa.\n\n"
                        + "Bắn trúng: giữ lượt. Bắn trượt: đổi lượt.\n"
                        + "Hết 15 giây: mất lượt. Đánh chìm cả 5 tàu để thắng.\n"
                        + "Thắng +3 điểm. Sau trận, có thể đề nghị chơi lại.\n\n"
                        + "4. Kết thúc trận: xem bảng WIN / LOSE rồi cả hai hải phận mở ra.\n"
                        + "5. Mở Lịch sử đấu ở cột bên trái, nhấn vào một dòng để xem lại\n"
                        + "toàn bộ hải phận và đường đạn của trận đó.\n"
                        + "6. Cài đặt: chỉnh âm lượng, sóng biển và bản đồ thu nhỏ.",
                "Hướng dẫn chỉ huy", JOptionPane.PLAIN_MESSAGE);
    }

    @Override
    public void dispose() {
        if (queueTimer != null) {
            queueTimer.stop();
        }
        if (penaltyTimer != null) {
            penaltyTimer.stop();
        }
        super.dispose();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object item) {
        return item instanceof Map ? (Map<String, Object>) item : null;
    }
}
