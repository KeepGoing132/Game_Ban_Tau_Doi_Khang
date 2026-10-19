package client.ui;

import common.protocol.JsonUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Match history of one commander: result, opponent, points, shots, duration and how the match
 * ended. Opened from the lobby for the logged-in player or for a selected opponent.
 */
public class MatchHistoryDialog extends JDialog {
    private static final SimpleDateFormat WHEN = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private final JTable table;

    public MatchHistoryDialog(Window parent, Map<String, Object> data) {
        super(parent, "Battleship • Lịch sử đấu", ModalityType.APPLICATION_MODAL);
        String name = JsonUtil.getString(data, "username", "Chỉ huy");
        int games = JsonUtil.getInt(data, "total_games", 0);
        int wins = JsonUtil.getInt(data, "total_wins", 0);
        int points = JsonUtil.getInt(data, "total_points", 0);
        List<Object> rows = JsonUtil.getList(data, "matches");
        int played = rows == null ? 0 : rows.size();
        setSize(940, 660); setMinimumSize(new Dimension(760, 520)); setLocationRelativeTo(parent);

        OceanPanel root = new OceanPanel(new BorderLayout(0, 18), "LỊCH SỬ ĐẤU", name.toUpperCase(Locale.ROOT));
        root.finishIntro();
        root.setBorder(new EmptyBorder(24, 26, 20, 26));
        setContentPane(root);

        JPanel head = Theme.seaCard(170, 18); head.setLayout(new BorderLayout(18, 0));
        SeaDecor compass = new SeaDecor(SeaDecor.Kind.COMPASS); compass.setPreferredSize(new Dimension(62, 62));
        head.add(compass, BorderLayout.WEST);
        JPanel titles = Theme.stack();
        Theme.addStack(titles, Theme.label("HỒ SƠ CHIẾN ĐẤU  •  " + name, Theme.FONT_HEADER, Theme.SEA_FOAM), 4);
        Theme.addStack(titles, Theme.muted(played + " trận gần nhất được hiển thị, mới nhất ở trên cùng"), 0);
        head.add(titles, BorderLayout.CENTER);
        JPanel chips = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        chips.add(Theme.badge(points + " ĐIỂM", Theme.AMBER));
        chips.add(Theme.badge(wins + " THẮNG", Theme.MINT));
        chips.add(Theme.badge(games + " ĐÃ ĐẤU", Theme.AZURE));
        head.add(chips, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(new String[] {"Thời gian", "Đối thủ", "Kết quả", "Điểm", "Phát bắn", "Thời lượng", "Kết thúc"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        if (rows != null) for (Object item : rows) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) item;
            boolean won = JsonUtil.getBoolean(row, "won", false);
            long ended = JsonUtil.getLong(row, "ended_at_ms", 0);
            int seconds = (int) JsonUtil.getLong(row, "duration_seconds", 0);
            model.addRow(new Object[] {
                    ended <= 0 ? "Không rõ" : WHEN.format(new Date(ended)),
                    JsonUtil.getString(row, "opponent_name", "Không rõ"),
                    won ? "THẮNG" : "THUA",
                    (JsonUtil.getInt(row, "points", 0) > 0 ? "+" : "") + JsonUtil.getInt(row, "points", 0),
                    JsonUtil.getInt(row, "shots", 0),
                    seconds <= 0 ? "—" : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
                    describe(JsonUtil.getString(row, "reason", ""))});
        }
        JTable table = new JTable(model);
        this.table = table;
        Theme.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(5).setPreferredWidth(110);
        table.getColumnModel().getColumn(6).setPreferredWidth(200);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setFont(column == 2 ? Theme.FONT_BOLD : Theme.FONT_REGULAR);
                setForeground(columnColor(t, row, column, selected));
                setBackground(selected ? Theme.PANEL_LIGHT : (row % 2 == 0 ? Theme.PANEL_BG : Theme.blend(Theme.PANEL_BG, Theme.PANEL_LIGHT, .35f)));
                setBorder(new EmptyBorder(0, 12, 0, 12));
                return this;
            }
            private Color columnColor(JTable t, int row, int column, boolean selected) {
                if (selected) return Theme.PRIMARY;
                switch (column) {
                    case 0: return Theme.TEXT_MUTED;
                    case 1: return Theme.SEA_FOAM;
                    case 2: return "THẮNG".equals(t.getValueAt(row, 2)) ? Theme.MINT : Theme.CORAL;
                    case 3: return Theme.AMBER;
                    case 4: return Theme.AZURE;
                    case 5: return Theme.ORCHID;
                    default: return Theme.TEXT_MUTED;
                }
            }
        });
        JPanel tableCard = Theme.seaCard(178, 12); tableCard.setLayout(new BorderLayout());
        if (played == 0) tableCard.add(Theme.label("Chưa có trận nào được ghi lại.", Theme.FONT_REGULAR, Theme.TEXT_MUTED), BorderLayout.NORTH);
        tableCard.add(Theme.scroll(table), BorderLayout.CENTER);
        root.add(tableCard, BorderLayout.CENTER);

        JPanel footer = Theme.transparent(new BorderLayout(12, 0));
        JPanel legend = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 14, 2));
        legend.add(Theme.label("THẮNG", Theme.FONT_SMALL.deriveFont(Font.BOLD), Theme.MINT));
        legend.add(Theme.label("THUA", Theme.FONT_SMALL.deriveFont(Font.BOLD), Theme.CORAL));
        legend.add(Theme.muted("Kết quả do server ghi lại sau khi trận kết thúc"));
        footer.add(legend, BorderLayout.WEST);
        JButton close = Theme.button("Đóng", false);
        close.addActionListener(e -> dispose());
        footer.add(close, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Rows currently shown; used by the GUI tests. */
    public JTable rows() { return table; }

    private static String describe(String reason) {
        if ("ALL_SHIPS_DESTROYED".equals(reason)) return "Đánh chìm toàn bộ hạm đội";
        if ("SURRENDER".equals(reason)) return "Một bên rời trận";
        if ("DISCONNECTED".equals(reason)) return "Mất kết nối";
        if (reason == null || reason.isEmpty() || "LEGACY_UNKNOWN".equals(reason)) return "Không rõ";
        return reason;
    }
}
