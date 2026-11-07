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

public class LobbyFrame extends JFrame {
    private final ClientController controller;
    private final JLabel lblUserInfo = Theme.label("Chào mừng, chỉ huy", Theme.FONT_TITLE, Theme.TEXT_MAIN);
    private final JLabel lblPoints = Theme.label("0", Theme.FONT_TITLE, Theme.TEXT_MAIN);
    private final JLabel lblWins = Theme.label("0", Theme.FONT_TITLE, Theme.TEXT_MAIN);
    private final JLabel lblRate = Theme.label("0%", Theme.FONT_TITLE, Theme.TEXT_MAIN);
    private final RankCard rankCard = new RankCard();
    private final java.awt.CardLayout modeLayout = new java.awt.CardLayout();
    private final JPanel modeHost = Theme.transparent(modeLayout);
    private final JPanel modeTiles = Theme.transparent(new GridLayout(1, 3, 12, 0));
    private final JTextField txtFleetName = new JTextField();
    private final DefaultTableModel roomModel = new DefaultTableModel(new String[]{"Mã phòng", "Chủ phòng", "Điểm", "Chỗ"}, 0) {
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable tblRooms = new JTable(roomModel);
    private final JLabel lblRoomListStatus = Theme.muted("Đang tải danh sách phòng...");
    private final javax.swing.Timer roomRefresh = new javax.swing.Timer(4000, e -> requestRooms());

    private void requestRooms() {
        if (controller != null) {
            controller.requestRoomList();
        }
    }
    private JPanel fleetPreview;
    private JLabel shipyardStatus;
    private int selectedMode;

    private static javax.swing.border.Border swatchBorder(boolean chosen) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(chosen ? Theme.PRIMARY : Theme.BORDER_COLOR, chosen ? 2 : 1),
                new EmptyBorder(6, 8, 6, 8));
    }
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

        tableModel = new DefaultTableModel(new String[]{"ID", "Chỉ huy", "Điểm", "Cấp bậc", "Trạng thái"}, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblOnlineUsers = new JTable(tableModel);

        JPanel body = Theme.transparent(new BorderLayout(18, 0));
        main.add(Theme.scrollPage(body, 720, Theme.BG_DARK), BorderLayout.CENTER);
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
        Theme.addStack(top, rankCard, 14);
        JPanel stats = Theme.transparent(new GridLayout(1, 3, 14, 0));
        stats.add(stat("TỔNG ĐIỂM", lblPoints, "+3 điểm cho mỗi chiến thắng", Theme.AMBER));
        stats.add(stat("TRẬN THẮNG", lblWins, "Số trận thắng / đã chơi", Theme.MINT));
        stats.add(stat("TỶ LỆ THẮNG", lblRate, "", Theme.AZURE));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94));
        Theme.addStack(top, stats, 0);
        return top;
    }

    private static final class RankCard extends JPanel {
        private int points = -1;

        RankCard() {
            setOpaque(false);
            setBorder(new EmptyBorder(14, 16, 14, 16));
        }

        void setPoints(int value) {
            if (points == value) {
                return;
            }
            points = value;
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(240, 104); }
        @Override public Dimension getMinimumSize() { return new Dimension(120, 104); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 104); }

        @Override protected void paintComponent(Graphics graphics) {
            if (points < 0) {
                return;
            }
            int w = getWidth(), h = getHeight();
            Graphics2D g = Theme.graphics(graphics);
            Rank.Tier tier = Rank.of(points);
            Rank.Tier next = Rank.next(points);
            g.setPaint(new GradientPaint(0, 0, Theme.blend(new Color(8, 40, 54), tier.color(), .28f),
                    0, h, Theme.blend(new Color(12, 56, 70), tier.color(), .14f)));
            g.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);
            g.setColor(new Color(tier.color().getRed(), tier.color().getGreen(), tier.color().getBlue(), 150));
            g.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
            int insignia = Math.min(h - 12, 64);
            Rank.paintInsignia(g, 12, (h - insignia) / 2, insignia, points);
            int textX = 12 + insignia + 16;
            g.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
            g.setColor(Theme.TEXT_MUTED);
            g.drawString("CẤP BẬC HIỆN TẠI", textX, 32);
            g.setFont(Theme.FONT_HEADER);
            g.setColor(tier.color());
            g.drawString(tier.name().toUpperCase(Locale.ROOT), textX, 58);
            int barX = textX, barW = Math.max(60, w - barX - 22), barY = h - 24;
            g.setColor(new Color(255, 255, 255, 22));
            g.fillRoundRect(barX, barY, barW, 8, 8, 8);
            g.setColor(tier.color());
            g.fillRoundRect(barX, barY, Math.max(8, (int) (barW * Rank.progress(points))), 8, 8, 8);
            g.setFont(Theme.FONT_SMALL);
            g.setColor(Theme.TEXT_MUTED);
            String note = Rank.summary(points);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(note, Math.max(barX, barX + barW - metrics.stringWidth(note)), barY - 8);
            if (next != null) {
                String reached = points + " điểm";
                g.drawString(reached, barX, barY - 8);
            }
            g.dispose();
        }
    }

    private JPanel stat(String title, JLabel value, String detail, Color accent) {
        JPanel card = Theme.vividCard(Theme.blend(new Color(8, 40, 54), accent, .26f),
                Theme.blend(new Color(14, 62, 78), accent, .16f), 15);
        card.setLayout(new BorderLayout(0, 4));
        card.add(Theme.label(title, Theme.FONT_SMALL.deriveFont(Font.BOLD), accent), BorderLayout.NORTH);
        value.setForeground(accent);
        card.add(value, BorderLayout.CENTER);
        card.add(Theme.muted(detail), BorderLayout.SOUTH);
        return card;
    }

    private JPanel matchmakingCard() {
        JPanel hub = Theme.vividCard(new Color(10, 62, 74), new Color(14, 104, 112), 20);
        hub.setLayout(new BorderLayout(0, 12));
        JPanel head = Theme.stack();
        Theme.addStack(head, Theme.badge("SẢNH CHỜ", Theme.MINT), 10);
        Theme.addStack(head, Theme.label("Chọn chế độ ra khơi", Theme.FONT_TITLE.deriveFont(24f), Theme.SEA_FOAM), 6);
        Theme.addStack(head, lblQuickMatchStatus, 0);
        hub.add(head, BorderLayout.NORTH);

        ModeTile human = new ModeTile(0, "ĐẤU VỚI NGƯỜI", "Tìm đối thủ hoặc mở phòng riêng", Theme.MINT);
        ModeTile machine = new ModeTile(1, "ĐẤU VỚI MÁY", "Ba mức: Dễ · Thường · Khó", Theme.AMBER);
        ModeTile shipyard = new ModeTile(2, "XƯỞNG CHIẾN HẠM", "Sơn màu và đặt tên hạm đội", Theme.ORCHID);
        modeTiles.add(human); modeTiles.add(machine); modeTiles.add(shipyard);
        for (final ModeTile tile : new ModeTile[]{human, machine, shipyard}) {
            tile.addActionListener(e -> setMode(tile.mode));
        }
        modeTiles.setPreferredSize(new Dimension(0, 132));
        modeTiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));

        modeHost.add(humanBranch(), "human");
        modeHost.add(machineBranch(), "machine");
        modeHost.add(shipyardBranch(), "shipyard");
        modeHost.setPreferredSize(new Dimension(0, 208));
        modeHost.setMaximumSize(new Dimension(Integer.MAX_VALUE, 208));
        JPanel middle = Theme.stack();
        Theme.addStack(middle, modeTiles, 12);
        Theme.addStack(middle, modeHost, 0);
        hub.add(middle, BorderLayout.CENTER);
        setMode(0);
        return hub;
    }

    private void setMode(int mode) {
        selectedMode = mode;
        modeLayout.show(modeHost, mode == 0 ? "human" : mode == 1 ? "machine" : "shipyard");
        for (Component component : modeTiles.getComponents()) {
            ((ModeTile) component).setSelectedMode(((ModeTile) component).mode == mode);
        }
        if (mode == 0) {
            controller.requestRoomList();
            roomRefresh.start();
        } else {
            roomRefresh.stop();
        }
        modeHost.revalidate();
        modeHost.repaint();
        modeTiles.repaint();
    }

    private JPanel humanBranch() {
        JPanel branch = Theme.stack();
        Theme.styleButton(btnQuickMatch, Theme.MINT, Theme.BG_DARK);
        btnQuickMatch.addActionListener(e -> toggleMatchmakingQueue());
        btnQuickMatch.setText("Tìm đối thủ  →");
        JPanel row = Theme.transparent(new BorderLayout());
        row.add(btnQuickMatch, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        Theme.addStack(branch, row, 12);

        JPanel head = Theme.transparent(new BorderLayout());
        head.add(Theme.label("PHÒNG ĐANG MỞ", Theme.FONT_SMALL.deriveFont(Font.BOLD), Theme.SEA_HORIZON), BorderLayout.WEST);
        JButton refresh = Theme.button("Làm mới", false);
        refresh.setFont(Theme.FONT_SMALL);
        refresh.setBorder(new EmptyBorder(5, 9, 5, 9));
        refresh.addActionListener(e -> controller.requestRoomList());
        head.add(refresh, BorderLayout.EAST);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        Theme.addStack(branch, head, 6);

        Theme.styleTable(tblRooms);
        tblRooms.setRowHeight(26);
        tblRooms.getTableHeader().setPreferredSize(new Dimension(0, 26));
        tblRooms.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                int view = tblRooms.getSelectedRow();
                if (view < 0 || !btnJoin.isEnabled()) {
                    return;
                }
                Object code = roomModel.getValueAt(tblRooms.convertRowIndexToModel(view), 0);
                if (code == null) {
                    return;
                }
                txtRoomCode.setText(String.valueOf(code));
                joinRoom();
            }
        });
        JScrollPane scroll = Theme.scroll(tblRooms);
        scroll.setPreferredSize(new Dimension(0, 76));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        Theme.addStack(branch, scroll, 6);
        Theme.addStack(branch, lblRoomListStatus, 0);
        return branch;
    }

    public void updateRoomList(List<Object> rooms) {
        roomModel.setRowCount(0);
        int count = 0;
        if (rooms != null) {
            for (Object item : rooms) {
                Map<String, Object> room = asMap(item);
                if (room == null) {
                    continue;
                }
                int players = JsonUtil.getInt(room, "players", 1);
                int capacity = JsonUtil.getInt(room, "capacity", 2);
                roomModel.addRow(new Object[] {
                        JsonUtil.getString(room, "room_code", ""),
                        JsonUtil.getString(room, "host_name", "Không rõ"),
                        JsonUtil.getInt(room, "host_points", 0),
                        players + "/" + capacity});
                count++;
            }
        }
        lblRoomListStatus.setText(count == 0
                ? "Chưa có phòng nào đang mở — tạo phòng riêng rồi gửi mã cho bạn bè."
                : count + " phòng đang mở, bấm một dòng để vào phòng đó.");
    }

    private JPanel machineBranch() {
        JPanel branch = Theme.stack();
        Theme.styleButton(btnBot, Theme.AMBER, Theme.BG_DARK);
        btnBot.addActionListener(e -> botDialog());
        btnBot.setText("Chọn mức độ  →");
        JPanel row = Theme.transparent(new BorderLayout());
        row.add(btnBot, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        Theme.addStack(branch, row, 10);
        Theme.addStack(branch, Theme.muted("Máy không tính điểm xếp hạng. Ván tập vẫn lưu vào lịch sử đấu."), 0);
        return branch;
    }

    private JPanel shipyardBranch() {
        JPanel branch = Theme.transparent(new BorderLayout(12, 8));
        fleetPreview = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                FleetSkin.paintFleetPreview((Graphics2D) graphics, 0, 0, getWidth(), getHeight());
            }
        };
        fleetPreview.setOpaque(false);
        fleetPreview.setPreferredSize(new Dimension(200, 132));
        branch.add(fleetPreview, BorderLayout.WEST);

        JPanel side = Theme.transparent(new BorderLayout(0, 8));
        JPanel swatches = Theme.transparent(new GridLayout(2, 3, 8, 8));
        final FleetSkin.Scheme[] schemes = FleetSkin.schemes();
        final JButton[] chips = new JButton[schemes.length];
        for (int i = 0; i < schemes.length; i++) {
            final int index = i;
            JButton chip = new JButton(schemes[i].name());
            chip.setFont(Theme.FONT_SMALL);
            chip.setForeground(schemes[i].hull());
            chip.setContentAreaFilled(false);
            chip.setOpaque(false);
            chip.setFocusPainted(false);
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chip.setBorder(swatchBorder(index == FleetSkin.index()));
            chip.addActionListener(e -> {
                FleetSkin.apply(index, txtFleetName.getText());
                for (int other = 0; other < chips.length; other++) {
                    chips[other].setBorder(swatchBorder(other == index));
                }
                fleetPreview.repaint();
                status(shipyardStatus, "Đã sơn " + schemes[index].name().toLowerCase(Locale.ROOT) + " cho hạm đội.");
            });
            chips[i] = chip;
            swatches.add(chip);
        }
        side.add(swatches, BorderLayout.NORTH);
        JPanel rename = Theme.transparent(new BorderLayout(8, 0));
        Theme.styleField(txtFleetName);
        txtFleetName.setText(FleetSkin.fleetName());
        txtFleetName.setPreferredSize(new Dimension(150, 38));
        JButton save = Theme.button("Đặt tên", false);
        save.setFont(Theme.FONT_SMALL);
        save.setBorder(new EmptyBorder(8, 12, 8, 12));
        save.addActionListener(e -> {
            FleetSkin.apply(FleetSkin.index(), txtFleetName.getText());
            status(shipyardStatus, "Hạm đội của bạn giờ tên là \"" + FleetSkin.fleetName() + "\".");
        });
        rename.add(txtFleetName, BorderLayout.CENTER);
        rename.add(save, BorderLayout.EAST);
        side.add(rename, BorderLayout.CENTER);
        shipyardStatus = Theme.muted("Màu này chỉ đổi hạm đội của bạn trên màn hình này.");
        side.add(shipyardStatus, BorderLayout.SOUTH);
        branch.add(side, BorderLayout.CENTER);
        return branch;
    }

    private static void status(JLabel label, String text) {
        if (label != null) {
            label.setText(text);
        }
    }

    private static final class ModeTile extends JButton {
        private final int mode;
        private final String title, subtitle;
        private final Color accent;
        private boolean chosen;

        ModeTile(int mode, String title, String subtitle, Color accent) {
            this.mode = mode;
            this.title = title;
            this.subtitle = subtitle;
            this.accent = accent;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 12, 10, 12));
            setRolloverEnabled(true);
            setText(title);
            setToolTipText(subtitle);
        }

        void setSelectedMode(boolean value) {
            chosen = value;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Theme.graphics(graphics);
            int w = getWidth(), h = getHeight();
            int label = 42, inset = 3;
            HubArt.paint(g, mode, inset, inset, w - inset * 2, h - label - 4,
                    chosen ? 1 : getModel().isRollover() ? .55 : 0);
            g.setColor(new Color(4, 20, 30, chosen ? 234 : 206));
            g.fillRoundRect(inset, h - label - 6, w - inset * 2, label + 3, 12, 12);
            g.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
            g.setColor(chosen ? accent : Theme.TEXT_MAIN);
            g.drawString(title, 13, h - label + 15);
            g.setFont(Theme.FONT_SMALL);
            g.setColor(Theme.TEXT_MUTED);
            g.drawString(subtitle, 13, h - 5);
            g.setColor(chosen ? accent : new Color(255, 255, 255, 34));
            g.setStroke(new BasicStroke(chosen ? 2f : 1.2f));
            g.drawRoundRect(1, 1, w - 3, h - 3, 16, 16);
            g.dispose();
        }
    }

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

    public void updateUserInfo(User user) {
        if (user == null) {
            return;
        }
        currentUserId = user.getUserId();
        lblUserInfo.setText("Chào mừng, " + user.getUsername());
        lblPoints.setText(String.valueOf(user.getTotalPoints()));
        rankCard.setPoints(user.getTotalPoints());
        lblWins.setText(user.getTotalWins() + " / " + user.getTotalGames());
        lblRate.setText(String.format(Locale.ROOT, "%.0f%%", user.getWinRate()));
        updateActions();
    }

    public int pointsOf(int userId) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            Object id = tableModel.getValueAt(row, 0);
            if (id instanceof Integer && (Integer) id == userId) {
                Object points = tableModel.getValueAt(row, 2);
                return points instanceof Integer ? (Integer) points : 0;
            }
        }
        return 0;
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
                int points = JsonUtil.getInt(user, "total_points", 0);
                tableModel.addRow(new Object[]{id, JsonUtil.getString(user, "username", ""),
                        points, Rank.of(points).name(),
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

    public void setConnectionStatus(String value) {
        lblConnection.setText("<html>" + Theme.html(value) + "</html>");
    }

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
