package client.ui;

import common.protocol.JsonUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class LeaderboardDialog extends JDialog {

    public LeaderboardDialog(Frame parent, List<Object> leaderboardData) {
        super(parent, "BẢNG XẾP HẠNG TOÀN HỆ THỐNG", true);
        setSize(650, 450);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(Theme.BG_DARK);
        setLayout(new BorderLayout(10, 10));

        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        headerPanel.setBackground(Theme.BG_DARK);
        JLabel title = new JLabel("🏆 BẢNG XẾP HẠNG ANH HÙNG HẢI CHIẾN 🏆");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.WARNING);
        headerPanel.add(title);
        add(headerPanel, BorderLayout.NORTH);

        // Table
        String[] columns = {"Hạng", "Tên người chơi", "Tổng điểm", "Số trận thắng", "Tổng số trận", "Tỷ lệ thắng"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        if (leaderboardData != null) {
            for (Object obj : leaderboardData) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    int rank = JsonUtil.getInt(map, "rank", 0);
                    String rankDisplay;
                    if (rank == 1) rankDisplay = "🥇 1";
                    else if (rank == 2) rankDisplay = "🥈 2";
                    else if (rank == 3) rankDisplay = "🥉 3";
                    else rankDisplay = String.valueOf(rank);

                    String username = JsonUtil.getString(map, "username", "");
                    int pts = JsonUtil.getInt(map, "total_points", 0);
                    int wins = JsonUtil.getInt(map, "total_wins", 0);
                    int games = JsonUtil.getInt(map, "total_games", 0);
                    double winRate = JsonUtil.getDouble(map, "win_rate", 0.0);

                    model.addRow(new Object[]{
                            rankDisplay,
                            username,
                            pts + " pts",
                            wins + " trận",
                            games + " trận",
                            winRate + "%"
                    });
                }
            }
        }

        JTable table = new JTable(model);
        table.setFont(Theme.FONT_REGULAR);
        table.setRowHeight(32);
        table.setBackground(Theme.PANEL_BG);
        table.setForeground(Theme.TEXT_MAIN);
        table.setGridColor(Theme.PANEL_LIGHT);
        table.getTableHeader().setFont(Theme.FONT_BOLD);
        table.getTableHeader().setBackground(Theme.PANEL_LIGHT);
        table.getTableHeader().setForeground(Theme.TEXT_MAIN);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(Theme.BG_DARK);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(Theme.BG_DARK);
        JButton closeBtn = new JButton("Đóng");
        Theme.styleButton(closeBtn, Theme.PANEL_LIGHT, Theme.TEXT_MAIN);
        closeBtn.addActionListener(e -> dispose());
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }
}
