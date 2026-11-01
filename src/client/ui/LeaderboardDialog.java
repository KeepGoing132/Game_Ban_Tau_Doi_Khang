package client.ui;

import common.protocol.JsonUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Podium and full ranking table, opened from the lobby. */
public class LeaderboardDialog extends JDialog {
    private static final Color[] MEDALS = {Theme.GOLD, new Color(212, 224, 240), new Color(226, 150, 110)};
    private static final String[] MEDAL_NAMES = {"DẪN ĐẦU", "BÁM ĐUỔI", "BỆ PHÓNG"};

    public LeaderboardDialog(Frame parent, List<Object> data) {
        super(parent, "Battleship • Bảng xếp hạng", true);
        setSize(830, 610);
        setMinimumSize(new Dimension(720, 500));
        setLocationRelativeTo(parent);

        JPanel root = Theme.transparent(new BorderLayout(0, 22));
        root.setOpaque(true);
        root.setBackground(Theme.BG_DARK);
        root.setBorder(new EmptyBorder(28, 28, 24, 28));
        setContentPane(root);

        JPanel top = Theme.stack();
        Theme.addStack(top, Theme.heading("HỒ SƠ CHỈ HUY", "Bảng vàng hải chiến",
                "Mỗi chiến thắng là một bước tiến. Mỗi chỉ huy là một chiến lược."), 22);
        JPanel podium = Theme.transparent(new GridLayout(1, 3, 12, 0));
        for (int place = 0; place < 3; place++) {
            podium.add(podiumCard(place, entry(data, place)));
        }
        podium.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        Theme.addStack(top, podium, 0);
        root.add(top, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Hạng", "Chỉ huy", "Điểm", "Thắng", "Số trận", "Tỷ lệ thắng"}, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        if (data != null) {
            for (Object item : data) {
                Map<String, Object> entry = asMap(item);
                if (entry == null) {
                    continue;
                }
                model.addRow(new Object[]{
                        String.format("%02d", JsonUtil.getInt(entry, "rank", 0)),
                        JsonUtil.getString(entry, "username", ""),
                        JsonUtil.getInt(entry, "total_points", 0),
                        JsonUtil.getInt(entry, "total_wins", 0),
                        JsonUtil.getInt(entry, "total_games", 0),
                        String.format(Locale.ROOT, "%.1f%%", JsonUtil.getDouble(entry, "win_rate", 0))});
            }
        }
        JTable table = new JTable(model);
        Theme.styleTable(table);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                Color color = column == 0 ? (row < 3 ? Theme.GOLD : Theme.TEXT_MUTED)
                        : column == 1 ? Theme.SEA_FOAM
                        : column == 2 ? Theme.AMBER
                        : column == 3 ? Theme.MINT
                        : column == 4 ? Theme.AZURE
                        : Theme.ORCHID;
                setFont(column == 0 || column == 2 ? Theme.FONT_BOLD : Theme.FONT_REGULAR);
                setForeground(selected ? Theme.PRIMARY : color);
                setBackground(selected ? Theme.PANEL_LIGHT : (row % 2 == 0 ? Theme.PANEL_BG : Theme.blend(Theme.PANEL_BG, Theme.PANEL_LIGHT, .35f)));
                setBorder(new EmptyBorder(0, 12, 0, 12));
                return this;
            }
        });
        JPanel tableCard = Theme.card(Theme.PANEL_BG, 10);
        tableCard.setLayout(new BorderLayout());
        tableCard.add(Theme.scroll(table));
        root.add(tableCard, BorderLayout.CENTER);

        JPanel footer = Theme.transparent(new BorderLayout());
        footer.add(Theme.muted("Thắng +3 điểm  •  Thống kê từ các trận đã hoàn tất"), BorderLayout.WEST);
        JButton close = Theme.button("Trở về sảnh", false);
        close.addActionListener(e -> dispose());
        footer.add(close, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JPanel podiumCard(int place, Map<String, Object> entry) {
        Color accent = MEDALS[place];
        JPanel card = Theme.vividCard(Theme.blend(Theme.PANEL_BG, accent, .22f), Theme.blend(Theme.PANEL_LIGHT, accent, .14f), 16);
        card.setLayout(new BorderLayout(0, 8));
        card.add(Theme.label("0" + (place + 1) + "  /  " + MEDAL_NAMES[place], Theme.FONT_SMALL.deriveFont(Font.BOLD), accent), BorderLayout.NORTH);
        card.add(Theme.label(entry == null ? "Chờ chỉ huy" : JsonUtil.getString(entry, "username", ""), Theme.FONT_HEADER, Theme.SEA_FOAM), BorderLayout.CENTER);
        String detail = entry == null ? "Hãy ghi dấu ấn của bạn"
                : JsonUtil.getInt(entry, "total_points", 0) + " điểm  •  " + JsonUtil.getInt(entry, "total_wins", 0) + " trận thắng";
        card.add(Theme.muted(detail), BorderLayout.SOUTH);
        return card;
    }

    private static Map<String, Object> entry(List<Object> data, int place) {
        if (data == null || place >= data.size()) {
            return null;
        }
        return asMap(data.get(place));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object item) {
        return item instanceof Map ? (Map<String, Object>) item : null;
    }
}
