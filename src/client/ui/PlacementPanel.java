package client.ui;

import common.config.GameConfig;
import common.model.Board;
import common.model.Coordinate;
import common.model.Ship;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

public class PlacementPanel extends JPanel {
    public interface ReadyListener {
        void onReady(List<Ship> ships);
    }

    private final BoardGridPanel gridPanel;
    private final ReadyListener readyListener;

    private boolean isHorizontal = true;
    private int currentShipIndex = 0; // 0 to 4
    private final List<Ship> placedShips = new ArrayList<>();
    private final JButton btnRotate;
    private final JButton btnReady;
    private final JLabel lblInstruction;
    private final JComboBox<String> cbShips;

    private static final String[] SHIP_IDS = {"CARRIER", "BATTLESHIP", "CRUISER_1", "CRUISER_2", "DESTROYER"};
    private static final int[] SHIP_SIZES = GameConfig.SHIP_SIZES;
    private static final String[] SHIP_LABELS = GameConfig.SHIP_NAMES;

    public PlacementPanel(ReadyListener readyListener) {
        this.readyListener = readyListener;
        setLayout(new BorderLayout(15, 15));
        setBackground(Theme.BG_DARK);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Center: Bàn cờ đặt tàu
        gridPanel = new BoardGridPanel("BỐ TRÍ 5 TÀU CHIẾN (17 Ô)", true);
        add(gridPanel, BorderLayout.CENTER);

        // East: Bảng điều khiển công cụ đặt tàu
        JPanel sidePanel = Theme.createCardPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setPreferredSize(new Dimension(320, 500));

        JLabel sideTitle = new JLabel("TRUNG TÂM BỐ TRÍ");
        sideTitle.setFont(Theme.FONT_TITLE);
        sideTitle.setForeground(Theme.PRIMARY);
        sideTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidePanel.add(sideTitle);
        sidePanel.add(Box.createRigidArea(new Dimension(0, 15)));

        lblInstruction = new JLabel("<html>Chọn loại tàu, hướng đặt rồi <b>click vào ô bản đồ</b> để đặt tàu:</html>");
        lblInstruction.setFont(Theme.FONT_REGULAR);
        lblInstruction.setForeground(Theme.TEXT_MUTED);
        sidePanel.add(lblInstruction);
        sidePanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JLabel lblSelect = new JLabel("Chọn loại tàu:");
        lblSelect.setFont(Theme.FONT_BOLD);
        lblSelect.setForeground(Theme.TEXT_MAIN);
        sidePanel.add(lblSelect);
        sidePanel.add(Box.createRigidArea(new Dimension(0, 5)));

        cbShips = new JComboBox<>(SHIP_LABELS);
        cbShips.setFont(Theme.FONT_REGULAR);
        cbShips.setBackground(Theme.PANEL_LIGHT);
        cbShips.setForeground(Theme.TEXT_MAIN);
        cbShips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        cbShips.addActionListener(e -> currentShipIndex = cbShips.getSelectedIndex());
        sidePanel.add(cbShips);
        sidePanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Nút xoay hướng
        btnRotate = new JButton("Hướng: HÀNG NGANG ↔");
        Theme.styleButton(btnRotate, Theme.PANEL_LIGHT, Theme.TEXT_MAIN);
        btnRotate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        btnRotate.addActionListener(e -> {
            isHorizontal = !isHorizontal;
            btnRotate.setText("Hướng: " + (isHorizontal ? "HÀNG NGANG ↔" : "HÀNG DỌC ↕"));
        });
        sidePanel.add(btnRotate);
        sidePanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Nút Tự động xếp ngẫu nhiên (Rất tiện lợi cho người chơi)
        JButton btnRandom = new JButton("🎲 Xếp ngẫu nhiên tự động");
        Theme.styleButton(btnRandom, Theme.PRIMARY, Color.WHITE);
        btnRandom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        btnRandom.addActionListener(e -> randomizePlacement());
        sidePanel.add(btnRandom);
        sidePanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Nút Xóa đặt lại
        JButton btnReset = new JButton("🔄 Xóa đặt lại từ đầu");
        Theme.styleButton(btnReset, Theme.PANEL_LIGHT, Theme.TEXT_MUTED);
        btnReset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        btnReset.addActionListener(e -> resetPlacement());
        sidePanel.add(btnReset);
        sidePanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Nút Sẵn sàng
        btnReady = new JButton("✅ BẤM ĐỂ SẴN SÀNG (READY)");
        Theme.styleSuccessButton(btnReady);
        btnReady.setEnabled(false);
        btnReady.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        btnReady.addActionListener(e -> {
            btnReady.setEnabled(false);
            btnRotate.setEnabled(false);
            btnRandom.setEnabled(false);
            btnReset.setEnabled(false);
            cbShips.setEnabled(false);
            lblInstruction.setText("<html><b style='color:#10b981;'>ĐÃ SẴN SÀNG!</b> Đang chờ đối thủ đặt tàu...</html>");
            if (readyListener != null) {
                readyListener.onReady(new ArrayList<>(placedShips));
            }
        });
        sidePanel.add(btnReady);

        add(sidePanel, BorderLayout.EAST);

        // Click trên bàn cờ để đặt tàu thủ công
        gridPanel.setCellClickListener((x, y) -> placeCurrentShipAt(x, y));
    }

    private void placeCurrentShipAt(int x, int y) {
        int size = SHIP_SIZES[currentShipIndex];
        String shipId = SHIP_IDS[currentShipIndex];

        // Kiểm tra xem tàu này đã được đặt trước đó chưa -> nếu có thì xóa tàu cũ
        placedShips.removeIf(s -> s.getId().equals(shipId));

        // Kiểm tra biên
        if (isHorizontal && x + size > GameConfig.BOARD_SIZE) {
            JOptionPane.showMessageDialog(this, "Tàu vượt ra ngoài biên ngang!", "Lỗi đặt tàu", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!isHorizontal && y + size > GameConfig.BOARD_SIZE) {
            JOptionPane.showMessageDialog(this, "Tàu vượt ra ngoài biên dọc!", "Lỗi đặt tàu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<Coordinate> coords = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            coords.add(new Coordinate(isHorizontal ? (x + i) : x, isHorizontal ? y : (y + i)));
        }

        // Kiểm tra xem có đè lên tàu khác không
        for (Coordinate c : coords) {
            for (Ship existing : placedShips) {
                if (existing.contains(c)) {
                    JOptionPane.showMessageDialog(this, "Tàu bị chồng lấn lên vị trí tàu khác!", "Lỗi đặt tàu", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }

        placedShips.add(new Ship(shipId, size, coords));
        refreshGridVisuals();

        // Tự động nhảy sang tàu tiếp theo nếu chưa đặt đủ
        if (currentShipIndex < SHIP_SIZES.length - 1) {
            currentShipIndex++;
            cbShips.setSelectedIndex(currentShipIndex);
        }

        checkReadyCondition();
    }

    public void randomizePlacement() {
        placedShips.clear();
        Random rand = new Random();
        Set<Coordinate> occupied = new HashSet<>();

        for (int i = 0; i < SHIP_SIZES.length; i++) {
            int size = SHIP_SIZES[i];
            String shipId = SHIP_IDS[i];
            boolean placed = false;
            int attempts = 0;

            while (!placed && attempts < 500) {
                attempts++;
                boolean horiz = rand.nextBoolean();
                int maxX = horiz ? (GameConfig.BOARD_SIZE - size) : (GameConfig.BOARD_SIZE - 1);
                int maxY = horiz ? (GameConfig.BOARD_SIZE - 1) : (GameConfig.BOARD_SIZE - size);

                int startX = rand.nextInt(maxX + 1);
                int startY = rand.nextInt(maxY + 1);

                List<Coordinate> coords = new ArrayList<>();
                boolean clash = false;
                for (int s = 0; s < size; s++) {
                    Coordinate c = new Coordinate(horiz ? (startX + s) : startX, horiz ? startY : (startY + s));
                    if (occupied.contains(c)) {
                        clash = true;
                        break;
                    }
                    coords.add(c);
                }

                if (!clash) {
                    occupied.addAll(coords);
                    placedShips.add(new Ship(shipId, size, coords));
                    placed = true;
                }
            }
        }

        refreshGridVisuals();
        checkReadyCondition();
    }

    public void resetPlacement() {
        placedShips.clear();
        currentShipIndex = 0;
        cbShips.setSelectedIndex(0);
        gridPanel.resetAllCells();
        btnReady.setEnabled(false);
        lblInstruction.setText("<html>Chọn loại tàu, hướng đặt rồi <b>click vào ô bản đồ</b> để đặt tàu:</html>");
    }

    private void refreshGridVisuals() {
        gridPanel.resetAllCells();
        for (Ship s : placedShips) {
            for (Coordinate c : s.getCoordinates()) {
                gridPanel.setCellState(c.getX(), c.getY(), BoardGridPanel.CellState.SHIP);
            }
        }
    }

    private void checkReadyCondition() {
        Board tempBoard = new Board();
        tempBoard.setShips(placedShips);
        boolean valid = tempBoard.validateAllShips();
        btnReady.setEnabled(valid);
        if (valid) {
            lblInstruction.setText("<html><b style='color:#10b981;'>ĐÃ ĐẶT ĐỦ 5 TÀU HỢP LỆ!</b><br>Nhấn 'SẴN SÀNG' để bắt đầu.</html>");
        } else {
            lblInstruction.setText("<html>Còn " + (5 - placedShips.size()) + " tàu chưa được đặt.</html>");
        }
    }
}
