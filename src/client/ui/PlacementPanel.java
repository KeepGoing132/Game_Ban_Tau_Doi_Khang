package client.ui;

import common.config.GameConfig;
import common.model.Board;
import common.model.Coordinate;
import common.model.FleetLayout;
import common.model.Ship;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fleet deployment, staged at sea: the board floats over living water and the fleet panel is a wet
 * glass deck. The phase opens with the "cast off" entrance effect.
 */
public class PlacementPanel extends OceanPanel {
    public interface ReadyListener {
        void onReady(List<Ship> ships);
    }

    private static final String[] SHIP_IDS = {"CARRIER", "BATTLESHIP", "CRUISER_1", "CRUISER_2", "DESTROYER"};
    private static final String[] SHIP_NAMES = {"Tàu sân bay", "Thiết giáp hạm", "Tuần dương I", "Tuần dương II", "Khu trục hạm"};
    private static final int[] SHIP_SIZES = GameConfig.SHIP_SIZES;
    /** Each ship class carries its own colour, so the fleet list reads as a colourful roster. */
    private static final Color[] SHIP_ACCENTS = {Theme.AZURE, Theme.ORCHID, Theme.MAGENTA, Theme.MINT, Theme.AMBER};
    private static final int SHIP_COUNT = 5;
    private static final int BOARD_LIMIT = GameConfig.BOARD_SIZE;

    private final BoardGridPanel gridPanel;
    private final ReadyListener readyListener;
    private final List<Ship> placedShips = new ArrayList<>();
    private final JComboBox<String> cbShips = new JComboBox<>(SHIP_NAMES);
    private final JButton[] fleetButtons = new JButton[SHIP_COUNT];
    private final JButton btnRotate = Theme.button("Hướng ngang  ↔   [R]", false);
    private final JButton btnRandom = Theme.button("Xếp ngẫu nhiên", false);
    private final JButton btnReset = Theme.button("Đặt lại", false);
    private final JButton btnReady = Theme.button("Sẵn sàng chiến đấu  →", true);
    private final JButton btnLeave = Theme.button("×  THOÁT VỀ SẢNH", false);
    private final JButton btnSettings = Theme.button("Cài đặt", false);
    private final JButton btnPresets = Theme.button("Mẫu bố trí (5 ô lưu)", false);
    private final JLabel lblInstruction = Theme.label(" ", Theme.FONT_SMALL, Theme.TEXT_MUTED);
    private final JLabel lblProgress = Theme.label("0 / " + SHIP_COUNT + " tàu đã bố trí", Theme.FONT_BOLD, Theme.PRIMARY);
    private final JLabel lblOpponent = Theme.badge("●  Đối thủ đang bố trí", Theme.TEXT_MUTED);
    private final JLabel lblMatch = Theme.muted("Bố trí bí mật. Đối thủ không nhìn thấy tàu của bạn.");
    private final JLabel lblDeadline = Theme.muted("Đang nhận thời gian chuẩn bị...");
    private final JLabel lblConnection = Theme.muted("Đang đo kết nối...");
    private final WaveProgress progress = new WaveProgress(0, SHIP_COUNT);
    private final javax.swing.Timer placementClock = new javax.swing.Timer(200, e -> updatePlacementClock());
    private boolean locked;
    private boolean ready;
    private boolean opponentReady;
    private boolean isHorizontal = true;
    private int currentShipIndex;
    private int hoverX = -1;
    private int hoverY = -1;
    private Runnable cancelReady;
    private Runnable leaveListener;
    private client.model.FleetPresets presets;
    private long placementEndNanos;
    private boolean disposed;

    public PlacementPanel(ReadyListener readyListener) {
        super(new BorderLayout(22, 18), "RA KHƠI", "BỐ TRÍ HẠM ĐỘI  •  SẴN SÀNG CHO HẢI CHIẾN");
        this.readyListener = readyListener;
        setBorder(new EmptyBorder(24, 28, 24, 28));
        add(header(), BorderLayout.NORTH);

        gridPanel = new BoardGridPanel("HẢI PHẬN CỦA BẠN", true);
        gridPanel.setCaption("Rê chuột để xem trước  •  Nhấn R để xoay tàu");
        add(gridPanel, BorderLayout.CENTER);

        JPanel side = Theme.seaCard(156, 20);
        side.setLayout(new BorderLayout(0, 14));
        side.setPreferredSize(new Dimension(328, 0));
        add(side, BorderLayout.EAST);
        JScrollPane fleetScroll = Theme.scrollPage(fleetDeck(), 396, new Color(0, 0, 0, 0));
        Theme.transparentScroll(fleetScroll);
        side.add(fleetScroll, BorderLayout.CENTER);
        side.add(readyArea(), BorderLayout.SOUTH);

        add(footer(), BorderLayout.SOUTH);

        gridPanel.setCellClickListener(this::placeCurrentShipAt);
        gridPanel.setCellHoverListener(this::preview);
        bindKeys();
        checkReadyCondition();
        refreshFleet();
    }

    // ------------------------------------------------------------------ layout

    private JPanel header() {
        JPanel header = Theme.transparent(new BorderLayout(18, 0));
        SeaDecor compass = new SeaDecor(SeaDecor.Kind.COMPASS);
        compass.setPreferredSize(new Dimension(56, 56));
        header.add(compass, BorderLayout.WEST);

        JPanel heading = Theme.stack();
        Theme.SeaTitle title = new Theme.SeaTitle("Triển khai hạm đội", Theme.FONT_TITLE, Theme.MAGENTA, Theme.MINT);
        title.setShimmer(true);
        Theme.addStack(heading, title, 5);
        Theme.addStack(heading, lblMatch, 2);
        SeaDecor rule = new SeaDecor(SeaDecor.Kind.WAVES);
        rule.setPreferredSize(new Dimension(0, 8));
        rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        Theme.addStack(heading, rule, 0);
        header.add(heading, BorderLayout.CENTER);

        // leaving during deployment is free: no penalty, the room is simply closed for both sides
        btnLeave.setToolTipText("Rời phòng bố trí và trở về sảnh (ESC)");
        Theme.styleButton(btnLeave, Theme.CORAL, Theme.BG_DARK);
        btnLeave.addActionListener(e -> leaveNow());
        JPanel actions = Theme.stack();
        Theme.addStack(actions, btnLeave, 6);
        Theme.addStack(actions, lblOpponent, 4);
        Theme.addStack(actions, lblDeadline, 4);
        Theme.addStack(actions, lblConnection, 4);
        Theme.addStack(actions, seaState(), 0);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    /** Ship roster plus the placement tools, scrollable so a short window keeps every control. */
    private JPanel fleetDeck() {
        JPanel fleet = Theme.stack();
        JPanel fleetHead = Theme.transparent(new BorderLayout(10, 0));
        SeaDecor scope = new SeaDecor(SeaDecor.Kind.SONAR);
        scope.setPreferredSize(new Dimension(46, 46));
        fleetHead.add(scope, BorderLayout.WEST);
        JPanel fleetTitles = Theme.stack();
        Theme.addStack(fleetTitles, Theme.label("Biên đội của bạn", Theme.FONT_HEADER, Theme.SEA_FOAM), 4);
        Theme.addStack(fleetTitles, Theme.muted("Chọn tàu, sau đó chọn vị trí trên bản đồ."), 0);
        fleetHead.add(fleetTitles, BorderLayout.CENTER);
        Theme.addStack(fleet, fleetHead, 14);

        cbShips.addActionListener(e -> {
            currentShipIndex = cbShips.getSelectedIndex();
            refreshFleet();
            preview(hoverX, hoverY);
        });
        for (int i = 0; i < SHIP_COUNT; i++) {
            JButton button = shipButton(i);
            Theme.addStack(fleet, button, 7);
        }

        btnRotate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnRotate.addActionListener(e -> rotate());
        Theme.addStack(fleet, btnRotate, 12);

        btnRandom.setFont(Theme.FONT_SMALL);
        btnRandom.setBorder(new EmptyBorder(10, 8, 10, 8));
        btnRandom.addActionListener(e -> randomizePlacement());
        btnReset.setFont(Theme.FONT_SMALL);
        btnReset.setBorder(new EmptyBorder(10, 8, 10, 8));
        btnReset.addActionListener(e -> resetPlacement());
        JPanel tools = Theme.transparent(new GridLayout(1, 2, 8, 0));
        tools.add(btnRandom);
        tools.add(btnReset);
        tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        Theme.addStack(fleet, tools, 0);

        btnPresets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnPresets.addActionListener(e -> presetDialog());
        Theme.addStack(fleet, btnPresets, 0);
        return fleet;
    }

    /** One roster row: the class name and its silhouette, drawn from the ship size. */
    private JButton shipButton(final int index) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = Theme.graphics(graphics);
                Color accent = SHIP_ACCENTS[index];
                g.setColor(locked ? Theme.TEXT_MUTED : isPlaced(index) ? accent : Theme.blend(accent, new Color(18, 42, 74), .58f));
                int start = getWidth() - 94;
                for (int cell = 0; cell < SHIP_SIZES[index]; cell++) {
                    g.fillRoundRect(start + cell * 13, getHeight() / 2 - 4, 10, 8, 3, 3);
                }
                if (isPlaced(index)) {
                    g.setStroke(new BasicStroke(1.6f));
                    g.drawLine(12, getHeight() / 2, 15, getHeight() / 2 + 3);
                    g.drawLine(15, getHeight() / 2 + 3, 21, getHeight() / 2 - 4);
                }
                g.dispose();
            }
        };
        Theme.styleButton(button, Theme.PANEL_LIGHT, Theme.TEXT_MAIN);
        button.setFont(Theme.FONT_SMALL);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(13, 28, 13, 105));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.addActionListener(e -> cbShips.setSelectedIndex(index));
        fleetButtons[index] = button;
        return button;
    }

    private JPanel readyArea() {
        JPanel readyArea = Theme.stack();
        Theme.addStack(readyArea, lblProgress, 8);
        progress.setPreferredSize(new Dimension(270, 10));
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        progress.setForeground(Theme.MINT);
        Theme.addStack(readyArea, progress, 10);
        lblInstruction.setPreferredSize(new Dimension(270, 44));
        lblInstruction.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        Theme.addStack(readyArea, lblInstruction, 10);
        btnReady.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btnReady.addActionListener(e -> toggleReady());
        Theme.addStack(readyArea, btnReady, 0);
        return readyArea;
    }

    private JPanel footer() {
        JPanel footer = Theme.transparent(new BorderLayout(0, 6));
        SeaDecor footerSea = new SeaDecor(SeaDecor.Kind.HORIZON);
        footerSea.setPreferredSize(new Dimension(0, 42));
        footer.add(footerSea, BorderLayout.NORTH);
        JPanel footerText = Theme.transparent(new BorderLayout());
        footerText.add(Theme.label("05 TÀU  /  17 Ô  /  Không được chồng lấn", Theme.FONT_SMALL, Theme.SEA_HORIZON), BorderLayout.WEST);
        JPanel footerEast = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        footerEast.add(Theme.muted("Nhấn ESC hoặc nút THOÁT VỀ SẢNH để rời phòng bố trí."));
        btnSettings.addActionListener(e -> SettingsDialog.open(this));
        footerEast.add(btnSettings);
        footerText.add(footerEast, BorderLayout.EAST);
        footer.add(footerText, BorderLayout.SOUTH);
        return footer;
    }

    private void bindKeys() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('r'), "rotate");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('R'), "rotate");
        getActionMap().put("rotate", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                rotate();
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "leave");
        getActionMap().put("leave", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                leaveNow();
            }
        });
    }

    // ------------------------------------------------------------------ server face

    public void setMatchInfo(String opponent, Runnable leave) {
        lblMatch.setText("Đối thủ: " + opponent);
        leaveListener = leave;
        setIntroSubtitle("BỐ TRÍ HẠM ĐỘI  •  ĐỐI THỦ: " + opponent);
    }

    public void setPresets(client.model.FleetPresets value) {
        presets = value;
    }

    public void setConnectionStatus(String value) {
        lblConnection.setText(value);
    }

    public void setCancelReadyListener(Runnable listener) {
        cancelReady = listener;
    }

    /** Preparation countdown; the server decides what happens when it reaches zero. */
    public void setPlacementDeadline(long deadlineMs, long serverTimeMs) {
        placementEndNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, deadlineMs - serverTimeMs));
        updatePlacementClock();
        placementClock.start();
    }

    private void updatePlacementClock() {
        if (placementEndNanos == 0 || disposed) {
            return;
        }
        long seconds = Math.max(0, (placementEndNanos - System.nanoTime() + 999999999L) / 1000000000L);
        lblDeadline.setText(seconds == 0 ? "Hết giờ — chờ máy chủ xác nhận" : "Chuẩn bị: " + seconds + " giây");
        lblDeadline.setForeground(seconds <= 30 ? Theme.CORAL : Theme.MINT);
        if (seconds == 0) {
            placementClock.stop();
        }
    }

    public void setServerReady(boolean value) {
        ready = value;
        locked = value;
        lockControls();
        btnReady.setText(value ? "Hủy sẵn sàng" : "Sẵn sàng chiến đấu  →");
        btnReady.setEnabled(value || validFleet());
        if (value) {
            instruction(opponentReady ? "Cả hai đã sẵn sàng. Đang vào trận..." : "Bạn đã sẵn sàng. Chờ đối thủ hoàn tất.", Theme.PRIMARY);
        } else {
            checkReadyCondition();
        }
    }

    public void setPending() {
        locked = true;
        lockControls();
        btnReady.setEnabled(false);
        btnReady.setText("Đang xác nhận...");
        instruction("Đang chờ máy chủ xác nhận hạm đội...", Theme.TEXT_MUTED);
    }

    public void showPlacementError(String message) {
        setServerReady(ready);
        instruction(message, Theme.DANGER);
        btnLeave.setEnabled(true);
    }

    public void showReadyState(boolean value) {
        opponentReady = value;
        lblOpponent.setText(value ? "●  Đối thủ đã sẵn sàng" : "●  Đối thủ đang bố trí");
        lblOpponent.setForeground(value ? Theme.PRIMARY : Theme.TEXT_MUTED);
        if (ready) {
            instruction(value ? "Cả hai đã sẵn sàng. Đang vào trận..." : "Bạn đã sẵn sàng. Chờ đối thủ hoàn tất.", Theme.PRIMARY);
        }
    }

    public void showLeaveError() {
        btnLeave.setEnabled(true);
    }

    public void showNotice(String text) {
        instruction(text, Theme.WARNING);
    }

    public void disposePanel() {
        disposed = true;
        placementClock.stop();
        disposeOcean();
    }

    @Override
    public void removeNotify() {
        placementClock.stop();
        super.removeNotify();
    }

    // ------------------------------------------------------------------ placement

    private void toggleReady() {
        setPending();
        if (ready) {
            if (cancelReady != null) {
                cancelReady.run();
            }
        } else if (readyListener != null) {
            readyListener.onReady(new ArrayList<>(placedShips));
        }
    }

    /** Exit to the lobby from the button or from ESC; the request is only sent once. */
    private void leaveNow() {
        if (!btnLeave.isEnabled() || leaveListener == null) {
            return;
        }
        btnLeave.setEnabled(false);
        instruction("Đang trở về sảnh...", Theme.WARNING);
        leaveListener.run();
    }

    private void rotate() {
        if (locked) {
            return;
        }
        isHorizontal = !isHorizontal;
        btnRotate.setText(isHorizontal ? "Hướng ngang  ↔   [R]" : "Hướng dọc  ↕   [R]");
        preview(hoverX, hoverY);
    }

    private boolean canPlace(int x, int y) {
        int size = SHIP_SIZES[currentShipIndex];
        if (x < 0 || y < 0 || x + (isHorizontal ? size : 1) > BOARD_LIMIT || y + (isHorizontal ? 1 : size) > BOARD_LIMIT) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            Coordinate cell = new Coordinate(x + (isHorizontal ? i : 0), y + (isHorizontal ? 0 : i));
            for (Ship ship : placedShips) {
                // the ship being re-placed may overlap its own previous cells
                if (!ship.getId().equals(SHIP_IDS[currentShipIndex]) && ship.contains(cell)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void preview(int x, int y) {
        hoverX = x;
        hoverY = y;
        refreshGridVisuals();
        if (locked || x < 0 || y < 0) {
            return;
        }
        boolean valid = canPlace(x, y);
        for (int i = 0; i < SHIP_SIZES[currentShipIndex]; i++) {
            gridPanel.setCellState(x + (isHorizontal ? i : 0), y + (isHorizontal ? 0 : i),
                    valid ? BoardGridPanel.CellState.PREVIEW_VALID : BoardGridPanel.CellState.PREVIEW_INVALID);
        }
    }

    private void placeCurrentShipAt(int x, int y) {
        if (locked) {
            return;
        }
        if (!canPlace(x, y)) {
            instruction("Vị trí chưa hợp lệ. Tàu cần nằm trong bản đồ và không chồng lấn.", Theme.DANGER);
            return;
        }
        String id = SHIP_IDS[currentShipIndex];
        List<Coordinate> cells = new ArrayList<>();
        for (int i = 0; i < SHIP_SIZES[currentShipIndex]; i++) {
            cells.add(new Coordinate(x + (isHorizontal ? i : 0), y + (isHorizontal ? 0 : i)));
        }
        placedShips.removeIf(ship -> ship.getId().equals(id));
        placedShips.add(new Ship(id, SHIP_SIZES[currentShipIndex], cells));
        hoverX = hoverY = -1;
        GameAudio.play(GameAudio.Sound.SPLASH);
        // jump to the next ship that still needs a position
        for (int step = 1; step <= SHIP_COUNT; step++) {
            int next = (currentShipIndex + step) % SHIP_COUNT;
            if (!isPlaced(next)) {
                cbShips.setSelectedIndex(next);
                break;
            }
        }
        refreshGridVisuals();
        checkReadyCondition();
        refreshFleet();
    }

    private boolean isPlaced(int index) {
        for (Ship ship : placedShips) {
            if (ship.getId().equals(SHIP_IDS[index])) {
                return true;
            }
        }
        return false;
    }

    private void refreshFleet() {
        for (int i = 0; i < SHIP_COUNT; i++) {
            JButton button = fleetButtons[i];
            if (button == null) {
                continue;
            }
            Color accent = SHIP_ACCENTS[i];
            button.setText(SHIP_NAMES[i]);
            button.setBackground(i == currentShipIndex ? Theme.blend(new Color(11, 44, 76), accent, .34f)
                    : isPlaced(i) ? Theme.blend(new Color(12, 52, 80), accent, .14f)
                    : new Color(11, 42, 70));
            button.setForeground(i == currentShipIndex ? accent : Theme.TEXT_MAIN);
            button.setEnabled(!locked);
            button.repaint();
        }
    }

    public void randomizePlacement() {
        if (locked) {
            return;
        }
        placedShips.clear();
        Random random = new Random();
        Set<Coordinate> occupied = new HashSet<>();
        for (int i = 0; i < SHIP_COUNT; i++) {
            for (int attempt = 0; attempt < 1000; attempt++) {
                boolean horizontal = random.nextBoolean();
                int size = SHIP_SIZES[i];
                int x = random.nextInt(horizontal ? BOARD_LIMIT + 1 - size : BOARD_LIMIT);
                int y = random.nextInt(horizontal ? BOARD_LIMIT : BOARD_LIMIT + 1 - size);
                List<Coordinate> cells = new ArrayList<>();
                for (int cell = 0; cell < size; cell++) {
                    cells.add(new Coordinate(x + (horizontal ? cell : 0), y + (horizontal ? 0 : cell)));
                }
                if (!Collections.disjoint(occupied, cells)) {
                    continue;
                }
                placedShips.add(new Ship(SHIP_IDS[i], size, cells));
                occupied.addAll(cells);
                break;
            }
        }
        hoverX = hoverY = -1;
        refreshGridVisuals();
        checkReadyCondition();
        refreshFleet();
    }

    public void resetPlacement() {
        if (locked) {
            return;
        }
        placedShips.clear();
        hoverX = hoverY = -1;
        cbShips.setSelectedIndex(0);
        refreshGridVisuals();
        checkReadyCondition();
        refreshFleet();
    }

    private void refreshGridVisuals() {
        gridPanel.resetAllCells();
        gridPanel.setFleet(placedShips);
    }

    private void instruction(String text, Color color) {
        lblInstruction.setText("<html>" + Theme.html(text) + "</html>");
        lblInstruction.setForeground(color);
    }

    private boolean validFleet() {
        Board board = new Board();
        board.setShips(placedShips);
        return board.validateAllShips();
    }

    private void checkReadyCondition() {
        boolean valid = validFleet();
        btnReady.setEnabled(!locked && valid);
        progress.setValue(placedShips.size());
        lblProgress.setText(placedShips.size() + " / " + SHIP_COUNT + " tàu đã bố trí");
        instruction(valid ? "Hạm đội đã hoàn tất. Sẵn sàng khi bạn muốn."
                        : "Còn " + (SHIP_COUNT - placedShips.size()) + " tàu cần được bố trí.",
                valid ? Theme.PRIMARY : Theme.TEXT_MUTED);
    }

    private void lockControls() {
        btnPresets.setEnabled(!locked);
        btnRotate.setEnabled(!locked);
        btnRandom.setEnabled(!locked);
        btnReset.setEnabled(!locked);
        cbShips.setEnabled(!locked);
        gridPanel.setInteractionEnabled(!locked);
        refreshFleet();
        refreshGridVisuals();
    }

    // ------------------------------------------------------------------ saved layouts

    private void presetDialog() {
        if (locked || presets == null || disposed) {
            return;
        }
        Map<Integer, String> names = presets.names();
        String[] slots = new String[SHIP_COUNT];
        for (int i = 0; i < SHIP_COUNT; i++) {
            slots[i] = (i + 1) + ". " + names.getOrDefault(i, "Ô trống");
        }
        JComboBox<String> selected = new JComboBox<>(slots);
        int action = JOptionPane.showOptionDialog(this, selected, "Mẫu bố trí trên máy này", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new Object[]{"Dùng mẫu", "Lưu vào ô", "Đổi tên", "Xóa", "Đóng"}, "Dùng mẫu");
        if (locked || disposed || action < 0 || action > 3) {
            return;
        }
        int slot = selected.getSelectedIndex();
        try {
            if (action == 0) {
                applyPreset(presets.load(slot));
            } else if (action == 1 || action == 2) {
                String name = JOptionPane.showInputDialog(this, "Tên mẫu (1–32 ký tự)", names.getOrDefault(slot, "Bố trí " + (slot + 1)));
                if (name == null || locked || disposed) {
                    return;
                }
                if (action == 1) {
                    presets.save(slot, name, placedShips);
                } else {
                    presets.rename(slot, name);
                }
                showNotice("Đã lưu mẫu bố trí trên máy này.");
            } else {
                presets.delete(slot);
                showNotice("Đã xóa mẫu bố trí.");
            }
        } catch (Exception e) {
            showNotice("Không dùng/lưu được mẫu: " + e.getMessage());
        }
    }

    /** Applies a saved layout after re-mapping its ships onto the five standard classes. */
    public void applyPreset(List<Ship> ships) {
        if (locked || disposed) {
            return;
        }
        List<Ship> copy = FleetLayout.decode(FleetLayout.encode(ships));
        List<Ship> normalized = new ArrayList<>();
        for (int i = 0; i < SHIP_COUNT; i++) {
            Ship chosen = null;
            for (Ship ship : copy) {
                if (ship.getSize() == SHIP_SIZES[i]) {
                    chosen = ship;
                    break;
                }
            }
            if (chosen == null) {
                throw new IllegalArgumentException("Mẫu thiếu tàu");
            }
            normalized.add(new Ship(SHIP_IDS[i], SHIP_SIZES[i], chosen.getCoordinates()));
            copy.remove(chosen);
        }
        placedShips.clear();
        placedShips.addAll(normalized);
        hoverX = hoverY = -1;
        refreshGridVisuals();
        checkReadyCondition();
        refreshFleet();
    }
}
