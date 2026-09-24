package client.ui;

import common.config.GameConfig;
import common.model.Coordinate;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class BoardGridPanel extends JPanel {
    public enum CellState {
        WATER,
        SHIP,
        HIT,
        MISS,
        SUNK,
        PREVIEW_VALID,
        PREVIEW_INVALID
    }

    public interface CellClickListener {
        void onCellClicked(int x, int y);
    }

    public interface CellHoverListener {
        void onCellHover(int x, int y);
    }

    private final JButton[][] cellButtons = new JButton[GameConfig.BOARD_SIZE][GameConfig.BOARD_SIZE];
    private final CellState[][] cellStates = new CellState[GameConfig.BOARD_SIZE][GameConfig.BOARD_SIZE];
    private CellClickListener clickListener;
    private CellHoverListener hoverListener;
    private Coordinate selectedCoordinate = null;

    public BoardGridPanel(String title, boolean interactive) {
        setLayout(new BorderLayout(5, 5));
        setBackground(Theme.PANEL_BG);
        setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Theme.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Tiêu đề bảng cờ
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(Theme.FONT_HEADER);
        titleLabel.setForeground(Theme.TEXT_MAIN);
        add(titleLabel, BorderLayout.NORTH);

        // Lưới 11x11 (gồm hàng tiêu đề A-J và cột 1-10)
        JPanel grid = new JPanel(new GridLayout(GameConfig.BOARD_SIZE + 1, GameConfig.BOARD_SIZE + 1, 2, 2));
        grid.setBackground(Theme.BG_DARK);

        // Góc trên cùng bên trái
        JLabel corner = new JLabel("", SwingConstants.CENTER);
        corner.setOpaque(true);
        corner.setBackground(Theme.BG_DARK);
        grid.add(corner);

        // Tiêu đề cột (A - J)
        for (int x = 0; x < GameConfig.BOARD_SIZE; x++) {
            char colChar = (char) ('A' + x);
            JLabel colHeader = new JLabel(String.valueOf(colChar), SwingConstants.CENTER);
            colHeader.setFont(Theme.FONT_GRID);
            colHeader.setForeground(Theme.TEXT_MUTED);
            colHeader.setOpaque(true);
            colHeader.setBackground(Theme.BG_DARK);
            grid.add(colHeader);
        }

        // Các hàng (1 - 10)
        for (int y = 0; y < GameConfig.BOARD_SIZE; y++) {
            JLabel rowHeader = new JLabel(String.valueOf(y + 1), SwingConstants.CENTER);
            rowHeader.setFont(Theme.FONT_GRID);
            rowHeader.setForeground(Theme.TEXT_MUTED);
            rowHeader.setOpaque(true);
            rowHeader.setBackground(Theme.BG_DARK);
            grid.add(rowHeader);

            for (int x = 0; x < GameConfig.BOARD_SIZE; x++) {
                final int cx = x;
                final int cy = y;
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(34, 34));
                btn.setMargin(new Insets(0, 0, 0, 0));
                btn.setFont(Theme.FONT_GRID);
                btn.setFocusPainted(false);
                btn.setBorder(new LineBorder(Theme.PANEL_LIGHT, 1));
                btn.setCursor(interactive ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());

                cellStates[x][y] = CellState.WATER;
                updateButtonVisual(btn, CellState.WATER);

                if (interactive) {
                    btn.addActionListener(e -> {
                        if (clickListener != null) {
                            clickListener.onCellClicked(cx, cy);
                        }
                    });
                    btn.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            if (hoverListener != null) {
                                hoverListener.onCellHover(cx, cy);
                            }
                        }
                    });
                }

                cellButtons[x][y] = btn;
                grid.add(btn);
            }
        }

        add(grid, BorderLayout.CENTER);
    }

    public void setCellClickListener(CellClickListener listener) {
        this.clickListener = listener;
    }

    public void setCellHoverListener(CellHoverListener listener) {
        this.hoverListener = listener;
    }

    public void setCellState(int x, int y, CellState state) {
        if (x < 0 || x >= GameConfig.BOARD_SIZE || y < 0 || y >= GameConfig.BOARD_SIZE) return;
        cellStates[x][y] = state;
        updateButtonVisual(cellButtons[x][y], state);
    }

    public CellState getCellState(int x, int y) {
        if (x < 0 || x >= GameConfig.BOARD_SIZE || y < 0 || y >= GameConfig.BOARD_SIZE) return null;
        return cellStates[x][y];
    }

    public void setSelectedCoordinate(Coordinate c) {
        if (selectedCoordinate != null) {
            updateButtonVisual(cellButtons[selectedCoordinate.getX()][selectedCoordinate.getY()], cellStates[selectedCoordinate.getX()][selectedCoordinate.getY()]);
        }
        selectedCoordinate = c;
        if (selectedCoordinate != null) {
            JButton btn = cellButtons[selectedCoordinate.getX()][selectedCoordinate.getY()];
            btn.setBorder(new LineBorder(Color.YELLOW, 2));
        }
    }

    public Coordinate getSelectedCoordinate() {
        return selectedCoordinate;
    }

    public void resetAllCells() {
        selectedCoordinate = null;
        for (int x = 0; x < GameConfig.BOARD_SIZE; x++) {
            for (int y = 0; y < GameConfig.BOARD_SIZE; y++) {
                cellStates[x][y] = CellState.WATER;
                updateButtonVisual(cellButtons[x][y], CellState.WATER);
            }
        }
    }

    private void updateButtonVisual(JButton btn, CellState state) {
        btn.setBorder(new LineBorder(Theme.PANEL_LIGHT, 1));
        switch (state) {
            case WATER:
                btn.setBackground(Theme.CELL_WATER);
                btn.setText("");
                break;
            case SHIP:
                btn.setBackground(Theme.CELL_SHIP);
                btn.setText("");
                break;
            case HIT:
                btn.setBackground(Theme.CELL_HIT);
                btn.setForeground(Color.WHITE);
                btn.setText("💥");
                break;
            case MISS:
                btn.setBackground(Theme.CELL_MISS);
                btn.setForeground(Theme.TEXT_MUTED);
                btn.setText("•");
                break;
            case SUNK:
                btn.setBackground(Theme.CELL_SUNK);
                btn.setForeground(Color.YELLOW);
                btn.setText("☠");
                break;
            case PREVIEW_VALID:
                btn.setBackground(new Color(16, 185, 129, 180));
                btn.setText("");
                break;
            case PREVIEW_INVALID:
                btn.setBackground(new Color(239, 68, 68, 180));
                btn.setText("");
                break;
        }
    }
}
