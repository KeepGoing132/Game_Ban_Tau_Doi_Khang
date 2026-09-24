package client.ui;

import common.config.GameConfig;
import common.model.Coordinate;
import common.model.Ship;
import common.model.ShotResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.List;

public class BattlePanel extends JPanel {
    public interface BattleActionListener {
        void onFire(int x, int y);
        void onSurrender();
        void onRematch();
        void onExitToLobby();
    }

    private final BattleActionListener actionListener;
    private final BoardGridPanel myBoardGrid;
    private final BoardGridPanel oppBoardGrid;

    private final JLabel lblTurnStatus;
    private final JLabel lblTimer;
    private final JLabel lblMyFleetStatus;
    private final JLabel lblOppFleetStatus;
    private final JButton btnFire;
    private final JButton btnSurrender;

    private boolean isMyTurn = false;
    private int remainingTime = 15;
    private Timer visualTimer;
    private int myRemainingShips = 5;
    private int oppRemainingShips = 5;

    public BattlePanel(String myName, String oppName, List<Ship> myShips, BattleActionListener actionListener) {
        this.actionListener = actionListener;
        setLayout(new BorderLayout(15, 10));
        setBackground(Theme.BG_DARK);
        setBorder(new EmptyBorder(15, 20, 15, 20));

        // Top Status Panel
        JPanel topPanel = Theme.createCardPanel();
        topPanel.setLayout(new BorderLayout(15, 5));

        JPanel namesPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        namesPanel.setOpaque(false);

        lblMyFleetStatus = new JLabel("⚓ BẠN: " + myName + " (Còn 5 tàu)", SwingConstants.LEFT);
        lblMyFleetStatus.setFont(Theme.FONT_HEADER);
        lblMyFleetStatus.setForeground(Theme.PRIMARY);

        lblOppFleetStatus = new JLabel("🎯 ĐỐI THỦ: " + oppName + " (Còn 5 tàu)", SwingConstants.RIGHT);
        lblOppFleetStatus.setFont(Theme.FONT_HEADER);
        lblOppFleetStatus.setForeground(Theme.DANGER);

        namesPanel.add(lblMyFleetStatus);
        namesPanel.add(lblOppFleetStatus);
        topPanel.add(namesPanel, BorderLayout.NORTH);

        JPanel turnBanner = new JPanel(new BorderLayout());
        turnBanner.setOpaque(false);
        lblTurnStatus = new JLabel("TRẬN ĐẤU BẮT ĐẦU!", SwingConstants.CENTER);
        lblTurnStatus.setFont(Theme.FONT_TITLE);
        lblTurnStatus.setForeground(Theme.WARNING);

        lblTimer = new JLabel("⏱ 15s", SwingConstants.RIGHT);
        lblTimer.setFont(Theme.FONT_TITLE);
        lblTimer.setForeground(Theme.TEXT_MAIN);

        turnBanner.add(lblTurnStatus, BorderLayout.CENTER);
        turnBanner.add(lblTimer, BorderLayout.EAST);
        topPanel.add(turnBanner, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);

        // Center Grids (My Board & Opponent Board)
        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 25, 0));
        boardsPanel.setOpaque(false);

        myBoardGrid = new BoardGridPanel("HẠM ĐỘI CỦA BẠN (ĐỐI THỦ BẮN VÀO ĐÂY)", false);
        oppBoardGrid = new BoardGridPanel("MỤC TIÊU ĐỐI THỦ (CLICK Ô ĐỂ BẮN)", true);

        // Khởi tạo các nút bấm trước khi gắn listener
        btnFire = new JButton("🚀 KHAI HỎA (BẮN) !");
        Theme.styleDangerButton(btnFire);
        btnFire.setFont(Theme.FONT_TITLE);
        btnFire.setPreferredSize(new Dimension(240, 50));
        btnFire.setEnabled(false);

        btnSurrender = new JButton("🏳️ Thoát / Đầu hàng");
        Theme.styleButton(btnSurrender, Theme.PANEL_LIGHT, Theme.TEXT_MUTED);
        btnSurrender.setPreferredSize(new Dimension(180, 50));

        // Vẽ tàu của mình lên myBoardGrid
        if (myShips != null) {
            for (Ship s : myShips) {
                for (Coordinate c : s.getCoordinates()) {
                    myBoardGrid.setCellState(c.getX(), c.getY(), BoardGridPanel.CellState.SHIP);
                }
            }
        }

        // Xử lý chọn ô trên bàn cờ đối thủ
        oppBoardGrid.setCellClickListener((x, y) -> {
            if (!isMyTurn) {
                JOptionPane.showMessageDialog(this, "Chưa đến lượt bắn của bạn!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            BoardGridPanel.CellState current = oppBoardGrid.getCellState(x, y);
            if (current == BoardGridPanel.CellState.HIT || current == BoardGridPanel.CellState.MISS || current == BoardGridPanel.CellState.SUNK) {
                JOptionPane.showMessageDialog(this, "Ô này bạn đã bắn rồi! Hãy chọn ô khác.", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            oppBoardGrid.setSelectedCoordinate(new Coordinate(x, y));
            btnFire.setEnabled(true);
        });

        boardsPanel.add(myBoardGrid);
        boardsPanel.add(oppBoardGrid);
        add(boardsPanel, BorderLayout.CENTER);

        // Bottom Action Panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 10));
        bottomPanel.setOpaque(false);

        btnFire.addActionListener(e -> {
            Coordinate selected = oppBoardGrid.getSelectedCoordinate();
            if (selected != null && isMyTurn) {
                btnFire.setEnabled(false);
                actionListener.onFire(selected.getX(), selected.getY());
            }
        });

        btnSurrender.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Bạn có chắc chắn muốn thoát trận?\nNếu thoát giữa chừng, bạn sẽ bị xử THUA ngay lập tức (0 điểm)!",
                    "Xác nhận thoát trận",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                actionListener.onSurrender();
            }
        });

        bottomPanel.add(btnFire);
        bottomPanel.add(btnSurrender);
        add(bottomPanel, BorderLayout.SOUTH);

        initVisualTimer();
    }

    private void initVisualTimer() {
        visualTimer = new Timer(1000, e -> {
            if (remainingTime > 0) {
                remainingTime--;
                lblTimer.setText("⏱ " + remainingTime + "s");
                if (remainingTime <= 5) {
                    lblTimer.setForeground(Theme.DANGER);
                } else {
                    lblTimer.setForeground(Theme.TEXT_MAIN);
                }
            }
        });
    }

    public void setTurn(boolean myTurn, int timeLimitSec) {
        this.isMyTurn = myTurn;
        this.remainingTime = timeLimitSec;
        lblTimer.setText("⏱ " + remainingTime + "s");
        lblTimer.setForeground(Theme.TEXT_MAIN);

        if (visualTimer != null) {
            visualTimer.restart();
        }

        if (myTurn) {
            lblTurnStatus.setText("⭐ LƯỢT BẮN CỦA BẠN! ⭐");
            lblTurnStatus.setForeground(Theme.SUCCESS);
            btnFire.setEnabled(oppBoardGrid.getSelectedCoordinate() != null);
        } else {
            lblTurnStatus.setText("⏳ ĐỐI THỦ ĐANG SUY NGHĨ...");
            lblTurnStatus.setForeground(Theme.TEXT_MUTED);
            btnFire.setEnabled(false);
        }
    }

    public void handleFireResult(boolean iAmShooter, Coordinate c, ShotResult result, List<Coordinate> sunkCoords, int oppShipsLeft, boolean keepTurn) {
        if (iAmShooter) {
            // Cập nhật lên bàn cờ đối phương (oppBoardGrid)
            if (result == ShotResult.MISS) {
                oppBoardGrid.setCellState(c.getX(), c.getY(), BoardGridPanel.CellState.MISS);
            } else if (result == ShotResult.HIT) {
                oppBoardGrid.setCellState(c.getX(), c.getY(), BoardGridPanel.CellState.HIT);
            } else if (result == ShotResult.SUNK) {
                oppBoardGrid.setCellState(c.getX(), c.getY(), BoardGridPanel.CellState.SUNK);
                if (sunkCoords != null) {
                    for (Coordinate sc : sunkCoords) {
                        oppBoardGrid.setCellState(sc.getX(), sc.getY(), BoardGridPanel.CellState.SUNK);
                    }
                }
            }
            oppRemainingShips = oppShipsLeft;
            lblOppFleetStatus.setText("🎯 ĐỐI THỦ: (Còn " + oppRemainingShips + " tàu)");
            oppBoardGrid.setSelectedCoordinate(null);
            btnFire.setEnabled(false);
        } else {
            // Đối thủ bắn vào bàn cờ của mình (myBoardGrid)
            if (result == ShotResult.MISS) {
                myBoardGrid.setCellState(c.getX(), c.getY(), BoardGridPanel.CellState.MISS);
            } else if (result == ShotResult.HIT) {
                myBoardGrid.setCellState(c.getX(), c.getY(), BoardGridPanel.CellState.HIT);
            } else if (result == ShotResult.SUNK) {
                myBoardGrid.setCellState(c.getX(), c.getY(), BoardGridPanel.CellState.SUNK);
                if (sunkCoords != null) {
                    for (Coordinate sc : sunkCoords) {
                        myBoardGrid.setCellState(sc.getX(), sc.getY(), BoardGridPanel.CellState.SUNK);
                    }
                }
                myRemainingShips--;
                lblMyFleetStatus.setText("⚓ BẠN: (Còn " + myRemainingShips + " tàu)");
            }
        }
    }

    public void showGameOver(boolean iWon, int awardedPoints, String reason) {
        if (visualTimer != null) {
            visualTimer.stop();
        }
        btnFire.setEnabled(false);
        btnSurrender.setEnabled(false);

        String title = iWon ? "🎉 CHIẾN THẮNG RỰC RỠ! 🎉" : "💀 BẠN ĐÃ THẤT TRẬN! 💀";
        String message = (iWon ? "Chúc mừng bạn đã giành chiến thắng!\n" : "Toàn bộ hạm đội đã bị đánh chìm!\n")
                + "Điểm nhận được: +" + awardedPoints + " điểm\n"
                + "Lý do: " + reason;

        Object[] options = {"Chơi lại (Rematch)", "Thoát về sảnh"};
        int choice = JOptionPane.showOptionDialog(
                this,
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                iWon ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == JOptionPane.YES_OPTION) {
            actionListener.onRematch();
        } else {
            actionListener.onExitToLobby();
        }
    }
}
