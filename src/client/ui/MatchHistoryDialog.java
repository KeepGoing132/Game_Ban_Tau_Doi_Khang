package client.ui;

import common.protocol.JsonUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MatchHistoryDialog extends JDialog {
    private static final SimpleDateFormat WHEN = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private final JTable table;
    private final DefaultTableModel model;
    private final JComboBox<String> resultFilter = new JComboBox<>(new String[]{"Tất cả", "Thắng", "Thua"});
    private final JTextField opponentFilter = new JTextField(12);
    private final JTextField fromDate = new JTextField(8), toDate = new JTextField(8);
    private final JButton moreButton = Theme.button("Xem thêm", false);
    private final JButton retryButton = Theme.button("Thử lại", false);
    private final JLabel profile = Theme.muted("Đang tải hồ sơ...");
    private final JLabel profileTitle = Theme.label("HỒ SƠ CHIẾN ĐẤU",Theme.FONT_HEADER,Theme.SEA_FOAM);
    private final JLabel pointsChip = Theme.badge("— ĐIỂM",Theme.AMBER);
    private final JLabel winsChip = Theme.badge("— THẮNG",Theme.MINT);
    private final JLabel gamesChip = Theme.badge("— ĐÃ ĐẤU",Theme.AZURE);
    private java.util.function.Consumer<Map<String,Object>> onQuery;
    private Map<String,Object> activeFilters = new java.util.LinkedHashMap<>();
    private Map<String,Object> lastQuery = new java.util.LinkedHashMap<>();
    private String nextCursor = "";
    private boolean loading;

    private final List<String> replayIds = new ArrayList<>();
    private final JLabel lblStatus;
    private final java.util.function.Consumer<String> onReplay;

    public MatchHistoryDialog(Window parent, Map<String, Object> data) {
        this(parent, data, null);
    }

    public MatchHistoryDialog(Window parent, Map<String, Object> data, java.util.function.Consumer<String> onReplay) {
        super(parent, "Battleship • Lịch sử đấu", ModalityType.MODELESS);
        this.onReplay = onReplay;
        String name = JsonUtil.getString(data, "username", "Chỉ huy");
        setSize(1080, 690); setMinimumSize(new Dimension(860, 540)); setLocationRelativeTo(parent);

        OceanPanel root = new OceanPanel(new BorderLayout(0, 18), "LỊCH SỬ ĐẤU", name.toUpperCase(Locale.ROOT));
        root.finishIntro();
        root.setBorder(new EmptyBorder(24, 26, 20, 26));
        setContentPane(root);

        JPanel head = Theme.seaCard(170, 18); head.setLayout(new BorderLayout(18, 0));
        SeaDecor compass = new SeaDecor(SeaDecor.Kind.COMPASS); compass.setPreferredSize(new Dimension(62, 62));
        head.add(compass, BorderLayout.WEST);
        JPanel titles = Theme.stack();
        profileTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
        profile.setMaximumSize(new Dimension(Integer.MAX_VALUE,26));
        Theme.addStack(titles, profileTitle, 4);
        Theme.addStack(titles, profile, 0);
        head.add(titles, BorderLayout.CENTER);
        JPanel chips = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        chips.add(pointsChip); chips.add(winsChip); chips.add(gamesChip);
        head.add(chips, BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);

        model = new DefaultTableModel(new String[] {"Thời gian", "Đối thủ", "Kết quả", "Điểm", "Phát bắn", "Thời lượng", "Kết thúc", "Hải phận"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        this.table = table;
        Theme.styleTable(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(230);
        table.getColumnModel().getColumn(7).setPreferredWidth(100);
        for (int column=0;column<table.getColumnCount();column++)
            table.getColumnModel().getColumn(column).setMinWidth(table.getColumnModel().getColumn(column).getPreferredWidth());
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setFont(column == 2 || column == 7 ? Theme.FONT_BOLD : Theme.FONT_REGULAR);
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
                    case 7: return "—".equals(t.getValueAt(row, 7)) ? Theme.TEXT_MUTED : Theme.PRIMARY;
                    default: return Theme.TEXT_MUTED;
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openSelected(); }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "replay");
        table.getActionMap().put("replay", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { openSelected(); }
        });
        JPanel tableCard = Theme.seaCard(178, 12); tableCard.setLayout(new BorderLayout());
        JPanel filters = Theme.transparent(new FlowLayout(FlowLayout.LEFT,6,8));
        Theme.styleField(opponentFilter); Theme.styleField(fromDate); Theme.styleField(toDate);
        opponentFilter.setPreferredSize(new Dimension(130,36));
        fromDate.setPreferredSize(new Dimension(106,36)); toDate.setPreferredSize(new Dimension(106,36));
        resultFilter.setBackground(Theme.PANEL_BG); resultFilter.setForeground(Theme.TEXT_MAIN); resultFilter.setFont(Theme.FONT_SMALL);
        filters.add(resultFilter); filters.add(Theme.muted("Đối thủ")); filters.add(opponentFilter);
        filters.add(Theme.muted("Từ")); filters.add(fromDate); filters.add(Theme.muted("Đến")); filters.add(toDate);
        JButton apply = Theme.button("Lọc",false); filters.add(apply);
        apply.addActionListener(e -> applyFilters());
        opponentFilter.addActionListener(e -> applyFilters());
        JPanel filterHeader = Theme.stack(); Theme.addStack(filterHeader,filters,2);
        Theme.addStack(filterHeader,Theme.muted("Ngày dd/MM/yyyy • " + java.time.ZoneId.systemDefault().getId() + " • Mới nhất trước"),8);
        tableCard.add(filterHeader,BorderLayout.NORTH);
        tableCard.add(Theme.scroll(table), BorderLayout.CENTER);
        root.add(tableCard, BorderLayout.CENTER);

        JPanel footer = Theme.transparent(new BorderLayout(12, 0));
        JPanel legend = Theme.stack();
        Theme.addStack(legend, Theme.muted("Nhấn vào một dòng để mở lại hải phận hai bên cùng toàn bộ đường đạn của trận đó."), 4);
        lblStatus = Theme.label(" ", Theme.FONT_SMALL, Theme.WARNING);
        Theme.addStack(legend, lblStatus, 0);
        footer.add(legend, BorderLayout.CENTER);
        JButton close = Theme.button("Đóng", false);
        close.addActionListener(e -> dispose());
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));
        moreButton.addActionListener(e -> { if (!loading && !nextCursor.isEmpty()) { Map<String,Object> q=new java.util.LinkedHashMap<>(activeFilters); q.put("cursor",nextCursor); query(q); } });
        retryButton.addActionListener(e -> { if (!loading) query(new java.util.LinkedHashMap<>(lastQuery)); });
        retryButton.setVisible(false); buttons.add(retryButton); buttons.add(moreButton); buttons.add(close);
        footer.add(buttons, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        acceptPage(data,false);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void addRows(List<Object> rows) {
        if (rows != null) for (Object item : rows) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) item;
            boolean won = JsonUtil.getBoolean(row, "won", false);
            long ended = JsonUtil.getLong(row, "ended_at_ms", 0);
            int seconds = (int) JsonUtil.getLong(row, "duration_seconds", 0);
            boolean replay = JsonUtil.getBoolean(row, "replay", false);
            model.addRow(new Object[] {
                    ended <= 0 ? "Không rõ" : WHEN.format(new Date(ended)),
                    JsonUtil.getString(row, "opponent_name", "Không rõ"),
                    won ? "THẮNG" : "THUA",
                    (JsonUtil.getInt(row, "points", 0) > 0 ? "+" : "") + JsonUtil.getInt(row, "points", 0),
                    JsonUtil.getInt(row, "shots", 0),
                    seconds <= 0 ? "—" : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
                    describe(JsonUtil.getString(row, "reason", "")),
                    replay ? "Xem lại" : "—"});
            replayIds.add(JsonUtil.getString(row, "match_id", ""));
        }
    }

    public void setQueryListener(java.util.function.Consumer<Map<String,Object>> listener) { onQuery=listener; }
    public void setLoading(boolean value) {
        loading=value; moreButton.setEnabled(!value && !nextCursor.isEmpty()); retryButton.setEnabled(!value);
        if (value) { retryButton.setVisible(false); showStatus("Đang tải lịch sử..."); }
    }
    public void showQueryError(String text) { setLoading(false); retryButton.setVisible(true); showStatus(text); }
    public void acceptPage(Map<String,Object> data, boolean append) {
        if (!append) { model.setRowCount(0); replayIds.clear(); }
        addRows(JsonUtil.getList(data,"matches"));
        nextCursor=JsonUtil.getBoolean(data,"has_more",false)?JsonUtil.getString(data,"next_cursor",""):"";
        setLoading(false); retryButton.setVisible(false);
        profileTitle.setText("HỒ SƠ  •  " + JsonUtil.getString(data,"username","Chỉ huy"));
        profile.setText("Đã tải " + model.getRowCount() + " trận • mới nhất ở trên cùng");
        pointsChip.setText(JsonUtil.getInt(data,"total_points",0) + " ĐIỂM");
        winsChip.setText(JsonUtil.getInt(data,"total_wins",0) + " THẮNG");
        gamesChip.setText(JsonUtil.getInt(data,"total_games",0) + " ĐÃ ĐẤU");
        showStatus(model.getRowCount()==0?"Không có trận phù hợp. Hãy thử bộ lọc khác.":"Đã tải " + model.getRowCount() + " trận. Nhấn vào dòng để xem lại.");
    }
    private void query(Map<String,Object> value) {
        if (onQuery==null) return;
        lastQuery=new java.util.LinkedHashMap<>(value); setLoading(true); onQuery.accept(value);
    }
    private void applyFilters() {
        try {
            Map<String,Object> filter=new java.util.LinkedHashMap<>();
            filter.put("result",new String[]{"ALL","WIN","LOSS"}[resultFilter.getSelectedIndex()]);
            filter.put("opponent",opponentFilter.getText().trim());
            java.time.format.DateTimeFormatter format=java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);
            java.time.ZoneId zone=java.time.ZoneId.systemDefault();
            long from=0,to=Long.MAX_VALUE;
            if (!fromDate.getText().trim().isEmpty()) from=java.time.LocalDate.parse(fromDate.getText().trim(),format).atStartOfDay(zone).toInstant().toEpochMilli();
            if (!toDate.getText().trim().isEmpty()) to=java.time.LocalDate.parse(toDate.getText().trim(),format).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1;
            if (from<0 || from>to) throw new IllegalArgumentException();
            filter.put("from_ms",from); filter.put("to_ms",to);
            activeFilters=filter; nextCursor=""; model.setRowCount(0); replayIds.clear(); query(filter);
        } catch (RuntimeException e) { showStatus("Ngày cần đúng dd/MM/yyyy; ngày bắt đầu không sau ngày kết thúc."); }
    }

    public JTable rows() { return table; }
    public void showStatus(String text) { lblStatus.setText(text == null ? " " : "<html>" + Theme.html(text) + "</html>"); }

    private void openSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || onReplay == null) return;
        int model = table.convertRowIndexToModel(row);
        if ("—".equals(table.getValueAt(row, 7))) {
            showStatus("Trận này được ghi trước khi có tính năng xem lại, nên hải phận không còn lưu.");
            return;
        }
        String matchId = model < replayIds.size() ? replayIds.get(model) : "";
        if (matchId.isEmpty()) { showStatus("Không xác định được mã trận."); return; }
        showStatus("Đang tải hải phận của trận đấu...");
        onReplay.accept(matchId);
    }

    static String describe(String reason) {
        if ("ALL_SHIPS_DESTROYED".equals(reason)) return "Đánh chìm toàn bộ hạm đội";
        if ("SURRENDER".equals(reason)) return "Một bên rời trận";
        if ("DISCONNECTED".equals(reason)) return "Mất kết nối";
        if (reason == null || reason.isEmpty() || "LEGACY_UNKNOWN".equals(reason)) return "Không rõ";
        return reason;
    }
}
