package client.ui;

import client.controller.ClientController;
import common.model.Coordinate;
import common.model.Ship;
import common.protocol.JsonUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replay of one finished match, opened from the player's own history: both sealed fleets on two
 * charts plus the ordered shot log, which can be played back shot by shot. The data is served by
 * the server only to the two players of that match, and only after the match is over.
 */
public class MatchReplayDialog extends JDialog {
    private static final SimpleDateFormat WHEN = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    /** One accepted shot of the stored log, already oriented to the requested player. */
    private static final class Shot {
        final int turn, x, y;
        final String result;
        final List<Coordinate> sunk;
        final boolean mine;
        Shot(Map<String, Object> raw, int myUserId) {
            turn = JsonUtil.getInt(raw, "turn_id", 0);
            x = JsonUtil.getInt(raw, "x", -1); y = JsonUtil.getInt(raw, "y", -1);
            result = JsonUtil.getString(raw, "result", "MISS");
            mine = JsonUtil.getInt(raw, "shooter_id", 0) == myUserId;
            List<Coordinate> cells = new ArrayList<>();
            List<Object> coords = JsonUtil.getList(raw, "sunk_ship_coords");
            if (coords != null) for (Object item : coords) if (item instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> cell = (Map<String, Object>) item;
                cells.add(new Coordinate(JsonUtil.getInt(cell, "x", -1), JsonUtil.getInt(cell, "y", -1)));
            }
            sunk = cells;
        }
        boolean valid() { return x >= 0 && x < 10 && y >= 0 && y < 10; }
    }

    private final BoardGridPanel myBoard = new BoardGridPanel("HẢI PHẬN CỦA BẠN", false);
    private final BoardGridPanel enemyBoard = new BoardGridPanel("HẢI PHẬN ĐỐI THỦ", false);
    private final List<Shot> shots = new ArrayList<>();
    private final List<Ship> myShips, enemyShips;
    private final DefaultListModel<String> shotModel = new DefaultListModel<>();
    private final JList<String> shotList = new JList<>(shotModel);
    private final JSlider slider = new JSlider(0, 1, 1);
    private final JLabel lblStep = Theme.label(" ", Theme.FONT_BOLD, Theme.SEA_FOAM);
    private final JButton btnPlay = Theme.button("Phát lại", true);
    private final JButton btnHead = Theme.button("Về đầu", false);
    private final JButton btnTail = Theme.button("Cuối", false);
    private final Timer player;
    private boolean updating;

    public MatchReplayDialog(Window parent, int myUserId, Map<String, Object> data) {
        super(parent, "Battleship • Hồi tượng trận đấu", ModalityType.MODELESS);
        String opponent = JsonUtil.getString(data, "opponent_name", "Không rõ");
        boolean won = JsonUtil.getBoolean(data, "won", false);
        int myShots = JsonUtil.getInt(data, "my_shots", 0), oppShots = JsonUtil.getInt(data, "opponent_shots", 0);
        long ended = JsonUtil.getLong(data, "ended_at_ms", 0), seconds = JsonUtil.getLong(data, "duration_seconds", 0);
        myShips = ClientController.parseFleet(JsonUtil.getList(data, "my_ships"));
        enemyShips = ClientController.parseFleet(JsonUtil.getList(data, "opponent_ships"));
        List<Object> raw = JsonUtil.getList(data, "shot_log");
        if (raw != null) for (Object item : raw) if (item instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> entry = (Map<String, Object>) item;
            Shot shot = new Shot(entry, myUserId);
            if (shot.valid()) shots.add(shot);
        }
        setSize(1280, 830); setMinimumSize(new Dimension(1040, 700));
        OceanPanel root = new OceanPanel(new BorderLayout(0, 14), "HỒI TƯỢNG TRẬN ĐẤU", opponent.toUpperCase(Locale.ROOT) + "  •  " + (won ? "BẠN ĐÃ THẮNG" : "BẠN ĐÃ THUA"));
        root.setBorder(new EmptyBorder(20, 22, 18, 22));
        setContentPane(root);

        JPanel head = Theme.seaCard(170, 14); head.setLayout(new BorderLayout(16, 0));
        SeaDecor compass = new SeaDecor(SeaDecor.Kind.COMPASS); compass.setPreferredSize(new Dimension(58, 58));
        head.add(compass, BorderLayout.WEST);
        JPanel titles = Theme.stack();
        Theme.addStack(titles, Theme.label("HỒI TƯỢNG  •  " + opponent, Theme.FONT_HEADER, Theme.SEA_FOAM), 4);
        Theme.addStack(titles, Theme.muted(ended <= 0 ? "Trận đã kết thúc" : "Kết thúc lúc " + WHEN.format(new Date(ended))
                + (seconds > 0 ? String.format(Locale.ROOT, "  •  kéo dài %d:%02d", seconds / 60, seconds % 60) : "")), 0);
        head.add(titles, BorderLayout.CENTER);
        JPanel chips = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        chips.add(Theme.badge(won ? "THẮNG" : "THUA", won ? Theme.MINT : Theme.CORAL));
        chips.add(Theme.badge((JsonUtil.getInt(data, "points", 0) > 0 ? "+" : "") + JsonUtil.getInt(data, "points", 0) + " ĐIỂM", Theme.AMBER));
        chips.add(Theme.badge(myShots + " PHÁT CỦA BẠN", Theme.AZURE));
        chips.add(Theme.badge(oppShots + " PHÁT CỦA ĐỐI THỦ", Theme.ORCHID));
        chips.add(Theme.badge(MatchResultView.reasonText(JsonUtil.getString(data, "reason", "")), Theme.VIOLET));
        head.add(chips, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        JPanel charts = Theme.transparent(new GridLayout(1, 2, 14, 0));
        charts.add(chart("HẢI PHẬN CỦA BẠN", myBoard, "Đối thủ đã bắn " + oppShots + " phát vào hạm đội của bạn"));
        charts.add(chart("HẢI PHẬN ĐỐI THỦ", enemyBoard, "Bạn đã bắn " + myShots + " phát vào hạm đội đối thủ"));
        root.add(charts, BorderLayout.CENTER);

        root.add(side(), BorderLayout.EAST);

        JPanel footer = Theme.transparent(new BorderLayout(14, 0));
        JPanel legend = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 14, 4));
        legend.add(Theme.label("× trúng", Theme.FONT_SMALL, Theme.CORAL));
        legend.add(Theme.label("○ trượt", Theme.FONT_SMALL, Theme.AZURE));
        legend.add(Theme.label("● chìm", Theme.FONT_SMALL, Theme.ORCHID));
        legend.add(Theme.muted("Tàu của hai bên chỉ được công bố sau khi trận kết thúc."));
        footer.add(legend, BorderLayout.CENTER);
        JButton close = Theme.button("Đóng", false);
        close.addActionListener(e -> dispose());
        footer.add(close, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        player = new Timer(360, e -> stepForward());
        player.setCoalesce(true);
        slider.addChangeListener(e -> { if (!updating) show(slider.getValue()); });
        btnPlay.addActionListener(e -> togglePlay());
        btnHead.addActionListener(e -> { player.stop(); btnPlay.setText("Phát lại"); show(0); });
        btnTail.addActionListener(e -> { player.stop(); btnPlay.setText("Phát lại"); show(shots.size()); });
        shotList.setCellRenderer(Theme.logRenderer());
        shotList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !updating && shotList.getSelectedIndex() >= 0) { player.stop(); btnPlay.setText("Phát lại"); show(shotList.getSelectedIndex() + 1); } });
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        show(shots.size());
    }

    private JPanel chart(String title, BoardGridPanel board, String caption) {
        board.setCompact(false);
        JPanel wrap = Theme.transparent(new BorderLayout(0, 6));
        wrap.add(board, BorderLayout.CENTER);
        JPanel foot = Theme.transparent(new BorderLayout());
        foot.add(Theme.label(caption, Theme.FONT_SMALL, Theme.SEA_HORIZON), BorderLayout.WEST);
        wrap.add(foot, BorderLayout.SOUTH);
        return wrap;
    }

    private JPanel side() {
        JPanel side = Theme.seaCard(170, 14); side.setLayout(new BorderLayout(0, 12));
        side.setPreferredSize(new Dimension(336, 0));
        JPanel head = Theme.stack();
        Theme.addStack(head, Theme.label("ĐƯỜNG ĐẠN CỦA TRẬN ĐẤU", Theme.FONT_HEADER, Theme.SEA_FOAM), 4);
        Theme.addStack(head, Theme.muted(shots.size() + " phát bắn được ghi lại theo đúng thứ tự"), 0);
        side.add(head, BorderLayout.NORTH);
        shotList.setOpaque(false); shotList.setBackground(new Color(0, 0, 0, 0));
        shotList.setForeground(Theme.TEXT_MAIN); shotList.setFont(Theme.FONT_REGULAR);
        shotList.setFixedCellHeight(26); shotList.setSelectionBackground(new Color(44, 120, 140, 150));
        JScrollPane scroll = Theme.scroll(shotList); Theme.transparentScroll(scroll);
        side.add(scroll, BorderLayout.CENTER);
        JPanel controls = Theme.stack();
        Theme.addStack(controls, lblStep, 10);
        slider.setOpaque(false); slider.setForeground(Theme.PRIMARY); slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        Theme.addStack(controls, slider, 10);
        JPanel row = Theme.transparent(new GridLayout(1, 3, 8, 0));
        row.add(btnHead); row.add(btnPlay); row.add(btnTail);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        Theme.addStack(controls, row, 0);
        side.add(controls, BorderLayout.SOUTH);
        return side;
    }

    private void togglePlay() {
        if (player.isRunning()) { player.stop(); btnPlay.setText("Phát lại"); return; }
        if (slider.getValue() >= shots.size()) show(0);
        player.start(); btnPlay.setText("Tạm dừng");
    }

    private void stepForward() {
        int next = slider.getValue() + 1;
        if (next >= shots.size()) { show(shots.size()); player.stop(); btnPlay.setText("Phát lại"); return; }
        show(next);
    }

    /** Rebuilds both charts up to the given shot index, so the replay is always consistent. */
    private void show(int count) {
        int upTo = Math.max(0, Math.min(count, shots.size()));
        updating = true;
        slider.setValue(upTo);
        shotModel.clear();
        for (int i = 0; i < upTo; i++) shotModel.addElement(line(i));
        if (upTo > 0) shotList.ensureIndexIsVisible(upTo - 1);
        updating = false;
        myBoard.resetAllCells(); myBoard.setFleet(myShips);
        enemyBoard.resetAllCells(); enemyBoard.setFleet(enemyShips);
        for (int i = 0; i < upTo; i++) {
            Shot shot = shots.get(i);
            BoardGridPanel target = shot.mine ? enemyBoard : myBoard;
            if ("SUNK".equals(shot.result)) {
                for (Coordinate cell : shot.sunk) target.setCellState(cell.getX(), cell.getY(), BoardGridPanel.CellState.SUNK);
                target.setCellState(shot.x, shot.y, BoardGridPanel.CellState.SUNK);
            } else target.setCellState(shot.x, shot.y, "HIT".equals(shot.result) ? BoardGridPanel.CellState.HIT : BoardGridPanel.CellState.MISS);
        }
        if (upTo == 0) lblStep.setText("Chưa bắn phát nào. Nhấn Phát lại để xem toàn bộ đường đạn.");
        else {
            Shot last = shots.get(upTo - 1);
            lblStep.setText(String.format(Locale.ROOT, "Phát %d / %d  •  %s  →  %s", upTo, shots.size(),
                    last.mine ? "Bạn" : "Đối thủ", BoardGridPanel.coordinate(last.x, last.y)));
        }
    }

    private String line(int index) {
        Shot shot = shots.get(index);
        String outcome = "MISS".equals(shot.result) ? "Trượt" : "HIT".equals(shot.result) ? "Trúng tàu" : "Đánh chìm";
        return String.format(Locale.ROOT, "%02d.  %s  →  %s  •  %s", index + 1, shot.mine ? "Bạn     " : "Đối thủ",
                BoardGridPanel.coordinate(shot.x, shot.y), outcome);
    }

    /** Rows currently listed; used by the GUI tests. */
    public JList<String> shots() { return shotList; }
    public BoardGridPanel myBoard() { return myBoard; }
    public BoardGridPanel enemyBoard() { return enemyBoard; }
    @Override public void dispose() { if (player != null) player.stop(); super.dispose(); }
}
