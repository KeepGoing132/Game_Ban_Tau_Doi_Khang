package client.ui;

import common.config.GameConfig;
import common.model.Coordinate;
import common.model.Ship;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The 10x10 battle grid, drawn as a pool of sea water: translucent glass tiles float over an
 * animated body of water with light caustics and a sweeping sonar, while fresh impacts leave
 * splashes and wreck bubbles behind. Interaction contract is unchanged.
 */
public class BoardGridPanel extends Theme.RoundedPanel {
    public enum CellState {
        WATER, SHIP, HIT, MISS, SUNK, PREVIEW_VALID, PREVIEW_INVALID
    }

    public interface CellClickListener {
        void onCellClicked(int x, int y);
    }

    public interface CellHoverListener {
        void onCellHover(int x, int y);
    }

    private static final int SIZE = GameConfig.BOARD_SIZE;
    private static final String[] STATE_NAMES = {"Vùng biển", "Tàu chiến", "Bắn trúng", "Bắn trượt", "Đã chìm", "Có thể đặt", "Không thể đặt"};

    private final JButton[][] cellButtons = new JButton[SIZE][SIZE];
    private final CellState[][] cellStates = new CellState[SIZE][SIZE];
    /** When each cell last changed to a splash/sink state, for the one-shot impact animations. */
    private final long[][] stateChangedNanos = new long[SIZE][SIZE];
    private final Ship[][] fleetCells = new Ship[SIZE][SIZE];
    private final JLabel titleLabel;
    private final JPanel grid;
    private final SeaDecor waves = new SeaDecor(SeaDecor.Kind.WAVES);
    private final SeaDecor compass = new SeaDecor(SeaDecor.Kind.COMPASS);
    private final JLabel statusLabel = Theme.muted("10 × 10  /  HẢI PHẬN");
    private CellClickListener clickListener;
    private CellHoverListener hoverListener;
    private Coordinate selectedCoordinate;
    private boolean interactionEnabled;
    private boolean compact;
    /** Whole static look of the board is baked once per size; only water motion is repainted. */
    private BufferedImage boardLayer;
    private BufferedImage waterTile;
    private int layerWidth;
    private int layerHeight;
    private int tileWidth;
    private int tileHeight;
    private long layerBuiltNanos;

    public BoardGridPanel(String title, boolean interactive) {
        super(Theme.SEA_GLASS, 190);
        setSheen(true);
        interactionEnabled = interactive;
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        compass.setPreferredSize(new Dimension(32, 32));
        JPanel header = Theme.transparent(new BorderLayout(10, 0));
        header.add(compass, BorderLayout.WEST);
        titleLabel = Theme.label(title, Theme.FONT_BOLD, Theme.SEA_FOAM);
        header.add(titleLabel, BorderLayout.CENTER);
        waves.setPreferredSize(new Dimension(0, 7));
        JPanel top = Theme.transparent(new BorderLayout(0, 7));
        top.add(header, BorderLayout.NORTH);
        top.add(waves, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        grid = Theme.transparent(new GridLayout(SIZE + 1, SIZE + 1, 3, 3));
        grid.add(new JLabel());
        for (int x = 0; x < SIZE; x++) {
            grid.add(axis(String.valueOf((char) ('A' + x))));
        }
        for (int y = 0; y < SIZE; y++) {
            grid.add(axis(String.valueOf(y + 1)));
            for (int x = 0; x < SIZE; x++) {
                grid.add(cell(x, y, interactive));
            }
        }
        add(squareHost(), BorderLayout.CENTER);
        statusLabel.setVisible(false);
        add(statusLabel, BorderLayout.SOUTH);
    }

    /** Builds one cell: its states, its tooltip and, on interactive boards, its input handling. */
    private JButton cell(final int x, final int y, boolean interactive) {
        cellStates[x][y] = CellState.WATER;
        JButton button = new CellButton(x, y);
        cellButtons[x][y] = button;
        button.setToolTipText(coordinate(x, y));
        button.getAccessibleContext().setAccessibleName(coordinate(x, y) + " • Vùng biển");
        button.setFocusable(interactive);
        button.setCursor(Cursor.getPredefinedCursor(interactive ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!interactive) {
            return button;
        }
        button.addActionListener(e -> {
            if (interactionEnabled && clickListener != null) {
                clickListener.onCellClicked(x, y);
            }
        });
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (interactionEnabled && hoverListener != null) {
                    hoverListener.onCellHover(x, y);
                }
            }

            public void mouseExited(MouseEvent e) {
                if (hoverListener != null) {
                    hoverListener.onCellHover(-1, -1);
                }
            }
        });
        bindArrows(button, x, y);
        button.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "select");
        button.getActionMap().put("select", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                button.doClick(0);
            }
        });
        return button;
    }

    /** Arrow keys walk the grid; ENTER fires, so the board is usable without a mouse. */
    private void bindArrows(JButton button, final int x, final int y) {
        String[] keys = {"LEFT", "RIGHT", "UP", "DOWN"};
        int[][] delta = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int i = 0; i < keys.length; i++) {
            final int dx = delta[i][0];
            final int dy = delta[i][1];
            button.getInputMap().put(KeyStroke.getKeyStroke(keys[i]), keys[i]);
            button.getActionMap().put(keys[i], new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    int nextX = Math.max(0, Math.min(SIZE - 1, x + dx));
                    int nextY = Math.max(0, Math.min(SIZE - 1, y + dy));
                    cellButtons[nextX][nextY].requestFocusInWindow();
                }
            });
        }
    }

    /** Keeps the grid square and centred, whatever space the layout gives the board. */
    private JPanel squareHost() {
        JPanel host = Theme.transparent(null);
        host.setLayout(new LayoutManager() {
            public void addLayoutComponent(String name, Component component) {
            }

            public void removeLayoutComponent(Component component) {
            }

            public Dimension preferredLayoutSize(Container parent) {
                return new Dimension(660, 660);
            }

            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(220, 220);
            }

            public void layoutContainer(Container parent) {
                int side = Math.min(parent.getWidth(), parent.getHeight());
                grid.setBounds((parent.getWidth() - side) / 2, (parent.getHeight() - side) / 2, side, side);
            }
        });
        host.add(grid);
        return host;
    }

    private JLabel axis(String text) {
        JLabel label = Theme.label(text, Theme.FONT_GRID, new Color(150, 220, 238));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    public static String coordinate(int x, int y) {
        return String.valueOf((char) ('A' + x)) + (y + 1);
    }

    public void setCaption(String text) {
        statusLabel.setText(text);
    }

    public void setFleet(List<Ship> fleet) {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                fleetCells[x][y] = null;
            }
        }
        if (fleet != null) {
            for (Ship ship : fleet) {
                for (Coordinate cell : ship.getCoordinates()) {
                    fleetCells[cell.getX()][cell.getY()] = ship;
                    setCellState(cell.getX(), cell.getY(), CellState.SHIP);
                }
            }
        }
        repaint();
    }

    /** Publishes the enemy fleet after the match: untouched water becomes ship, impact marks stay. */
    public void revealFleet(List<Ship> fleet) {
        if (fleet == null) {
            return;
        }
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                fleetCells[x][y] = null;
            }
        }
        for (Ship ship : fleet) {
            for (Coordinate cell : ship.getCoordinates()) {
                fleetCells[cell.getX()][cell.getY()] = ship;
            }
        }
        for (Ship ship : fleet) {
            for (Coordinate cell : ship.getCoordinates()) {
                if (cellStates[cell.getX()][cell.getY()] == CellState.WATER) {
                    setCellState(cell.getX(), cell.getY(), CellState.SHIP);
                }
            }
        }
        repaint();
    }

    /** Copies the visible marks of another board, for the after-action report. */
    public void mirrorStates(BoardGridPanel source) {
        if (source == null) {
            return;
        }
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                setCellState(x, y, source.getCellState(x, y));
            }
        }
        repaint();
    }

    public void setCompact(boolean value) {
        compact = value;
        int inset = value ? 6 : 16;
        setBorder(new EmptyBorder(inset, inset, inset, inset));
        titleLabel.setFont(value ? Theme.FONT_SMALL : Theme.FONT_HEADER);
        compass.setVisible(!value);
        compass.setPreferredSize(new Dimension(value ? 0 : 32, value ? 0 : 32));
        waves.setPreferredSize(new Dimension(0, value ? 4 : 7));
        ((GridLayout) grid.getLayout()).setHgap(value ? 1 : 3);
        ((GridLayout) grid.getLayout()).setVgap(value ? 1 : 3);
        for (Component child : grid.getComponents()) {
            if (child instanceof JLabel) {
                child.setFont(Theme.FONT_GRID.deriveFont(value ? 9f : 14f));
            }
        }
        revalidate();
    }

    public Point cellCenter(int x, int y, Component relative) {
        JButton cell = cellButtons[x][y];
        return SwingUtilities.convertPoint(cell, cell.getWidth() / 2, cell.getHeight() / 2, relative);
    }

    public void setInteractionEnabled(boolean value) {
        if (interactionEnabled == value) {
            return;
        }
        interactionEnabled = value;
        Cursor cursor = Cursor.getPredefinedCursor(value ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                cellButtons[x][y].setCursor(cursor);
            }
        }
        repaint();
    }

    public void setCellClickListener(CellClickListener listener) {
        clickListener = listener;
    }

    public void setCellHoverListener(CellHoverListener listener) {
        hoverListener = listener;
    }

    public void setCellState(int x, int y, CellState state) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
            return;
        }
        boolean impact = state == CellState.HIT || state == CellState.MISS || state == CellState.SUNK;
        if (cellStates[x][y] != state && impact) {
            stateChangedNanos[x][y] = System.nanoTime();
        }
        cellStates[x][y] = state;
        cellButtons[x][y].getAccessibleContext().setAccessibleName(coordinate(x, y) + " • " + STATE_NAMES[state.ordinal()]);
        cellButtons[x][y].repaint();
    }

    public CellState getCellState(int x, int y) {
        return x < 0 || x >= SIZE || y < 0 || y >= SIZE ? null : cellStates[x][y];
    }

    public void setSelectedCoordinate(Coordinate coordinate) {
        selectedCoordinate = coordinate;
        repaint();
    }

    public Coordinate getSelectedCoordinate() {
        return selectedCoordinate;
    }

    public void resetAllCells() {
        selectedCoordinate = null;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                stateChangedNanos[x][y] = 0;
                setCellState(x, y, CellState.WATER);
            }
        }
    }

    // ------------------------------------------------------------------ painting

    /** Glass deck, water body and the static chart marks, baked once; only motion is repainted. */
    @Override
    protected void paintComponent(Graphics graphics) {
        int w = getWidth(), h = getHeight();
        if (w < 20 || h < 20) {
            return;
        }
        Graphics2D g = Theme.graphics(graphics);
        BufferedImage layer = boardLayer(w, h);
        if (layerWidth == w && layerHeight == h) {
            g.drawImage(layer, 0, 0, null);
        } else {
            g.drawImage(layer, 0, 0, w, h, null);
        }
        double t = OceanScene.isAnimated() ? OceanScene.time() : 0;
        // a rectangular clip stays inside the rounded corners and costs nothing to rasterise
        g.clipRect(6, 6, w - 12, h - 12);
        for (int i = 0; i < 3; i++) {
            OceanScene.paintWaveRule(g, 4, (int) (h * (0.26 + i * 0.24)), w - 8, t * (1 + i * 0.35) + i * 2, Theme.SEA_FOAM, compact ? .08 : .16);
        }
        OceanScene.paintCaustics(g, 6, 6, w - 12, h - 12, t, compact ? .08 : .18);
        OceanScene.paintSonar(g, w / 2.0, h / 2.0, Math.min(w, h) * 0.52, t, 4.2, Theme.PRIMARY, 3, compact ? .14 : .22);
        g.dispose();
    }

    private BufferedImage boardLayer(int w, int h) {
        if (boardLayer != null && layerWidth == w && layerHeight == h) {
            return boardLayer;
        }
        long now = System.nanoTime();
        // a resize storm must not rebake the layer on every intermediate size
        if (boardLayer != null && (now - layerBuiltNanos) / 1000000L < 140) {
            return boardLayer;
        }
        layerWidth = w;
        layerHeight = h;
        layerBuiltNanos = now;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Theme.graphics(image);
        g.setColor(new Color(Theme.SEA_GLASS.getRed(), Theme.SEA_GLASS.getGreen(), Theme.SEA_GLASS.getBlue(), 190));
        g.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 28), 0, Math.max(1, (h - 1) / 2), new Color(255, 255, 255, 0)));
        g.fillRoundRect(1, 1, w - 2, h - 2, 19, 19);
        g.setColor(new Color(Theme.SEA_HORIZON.getRed(), Theme.SEA_HORIZON.getGreen(), Theme.SEA_HORIZON.getBlue(), 78));
        g.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
        g.clip(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 19, 19));
        // water: turquoise above the midline, then violet-tinted deep
        g.setPaint(new GradientPaint(0, 0, new Color(36, 172, 188, 204), 0, h * 0.55f, new Color(22, 104, 168, 214)));
        g.fillRect(1, 1, w - 2, h - 2);
        g.setPaint(new GradientPaint(0, h * 0.55f, new Color(22, 104, 168, 214), 0, h, new Color(24, 34, 96, 226)));
        g.fillRect(1, (int) (h * 0.55), w - 2, h - (int) (h * 0.55));
        paintDepthContours(g, w, h, 0);
        if (!compact) {
            OceanScene.paintCompassRose(g, w * 0.13, h * 0.87, Math.min(w, h) * 0.055, 0);
        }
        g.dispose();
        waterTile = null;
        return boardLayer = image;
    }

    /** Faint chart contours so the water reads as a nautical map rather than a flat gradient. */
    private void paintDepthContours(Graphics2D g, int w, int h, double t) {
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(156, 224, 244, 26));
        for (int i = 1; i <= 4; i++) {
            double inset = Math.min(w, h) * (0.10 + i * 0.11) + Math.sin(t * 0.6 + i) * 2.4;
            g.draw(new Ellipse2D.Double(w / 2.0 - inset * 1.35, h / 2.0 - inset, inset * 2.7, inset * 2));
        }
    }

    /** Splashes and wreck bubbles that sit above the tiles. */
    @Override
    protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        int w = getWidth(), h = getHeight();
        if (w < 20 || h < 20) {
            return;
        }
        Graphics2D g = Theme.graphics(graphics);
        double t = OceanScene.isAnimated() ? OceanScene.time() : 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                long changed = stateChangedNanos[x][y];
                if (changed == 0) {
                    continue;
                }
                double age = (System.nanoTime() - changed) / 1e9;
                if (age > 3.2) {
                    continue;
                }
                Point center = cellCenter(x, y, this);
                CellState state = cellStates[x][y];
                int size = Math.min(cellButtons[x][y].getWidth(), cellButtons[x][y].getHeight());
                if (state == CellState.MISS) {
                    OceanScene.paintRipple(g, center.x, center.y, age, size * 0.95);
                } else if (state == CellState.SUNK) {
                    double p = OceanScene.clamp01(age / 2.6);
                    g.setColor(new Color(5, 26, 52, (int) (150 * (1 - p))));
                    g.fillOval(center.x - size / 2, center.y - size / 2, size, size);
                    Graphics2D bubbles = (Graphics2D) g.create();
                    bubbles.translate(center.x - size / 2.0, 0);
                    OceanScene.paintBubbles(bubbles, size, center.y + size * 0.4, center.y - size * 0.9, t * 1.4, 5, 0.55 * (1 - p), x * 31 + y * 17);
                    bubbles.dispose();
                    g.setColor(new Color(226, 246, 255, (int) (200 * (1 - p))));
                    g.setStroke(new BasicStroke(1.4f));
                    g.drawOval(center.x - size / 2, center.y - size / 2, size, size);
                } else if (state == CellState.HIT && age < 0.45) {
                    double p = 1 - age / 0.45;
                    g.setColor(new Color(255, 236, 180, (int) (190 * p)));
                    g.fillOval(center.x - size / 2, center.y - size / 2, size, size);
                }
            }
        }
        g.dispose();
    }

    /** A single tile, drawn by hand: the button UI and the border passes are skipped on purpose. */
    private class CellButton extends JButton {
        private final int x;
        private final int y;

        CellButton(int x, int y) {
            this.x = x;
            this.y = y;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setMargin(new Insets(0, 0, 0, 0));
            setPreferredSize(new Dimension(34, 34));
            setBorder(null);
        }

        @Override
        public void paint(Graphics graphics) {
            paintComponent(graphics);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            CellState state = cellStates[x][y];
            boolean selected = selectedCoordinate != null && selectedCoordinate.getX() == x && selectedCoordinate.getY() == y;
            paintTileBackground(g, state, w, h);
            int cx = w / 2, cy = h / 2;
            if (state == CellState.SHIP) {
                paintShip(g, w, h, cx, cy);
            }
            paintMarks(g, state, w, h, cx, cy, selected);
        }

        /** Water keeps its glass tile; any other state is a coloured slab with an inner line. */
        private void paintTileBackground(Graphics2D g, CellState state, int w, int h) {
            Color fill = null;
            switch (state) {
                case SHIP:
                    fill = new Color(120, 112, 214, 216);
                    break;
                case HIT:
                    fill = new Color(178, 72, 82, 232);
                    break;
                case SUNK:
                    fill = new Color(88, 48, 92, 236);
                    break;
                case MISS:
                    fill = new Color(30, 58, 94, 206);
                    break;
                case PREVIEW_VALID:
                    fill = new Color(40, 132, 116, 224);
                    break;
                case PREVIEW_INVALID:
                    fill = new Color(126, 58, 68, 224);
                    break;
                default:
                    break;
            }
            boolean water = state == CellState.WATER;
            boolean hover = water && getModel().isRollover() && interactionEnabled;
            if (water && !hover) {
                g.drawImage(waterTile(w, h), 0, 0, null);
            } else if (!water) {
                g.setPaint(new GradientPaint(0, 0, Theme.blend(fill, Color.WHITE, .10f), w, h, fill));
                g.fillRoundRect(0, 0, w - 1, h - 1, 5, 5);
                g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 120));
                g.fillRect(3, h * 3 / 4, w - 6, 1);
            }
            if (hover) {
                g.setColor(new Color(96, 214, 236, 150));
                g.fillRoundRect(0, 0, w - 1, h - 1, 5, 5);
                g.setColor(Theme.CELL_WATER_HOVER);
                g.drawRoundRect(0, 0, w - 1, h - 1, 5, 5);
            }
            if (!water) {
                g.setColor(Theme.blend(fill, Theme.TEXT_MUTED, .16f));
                g.drawRoundRect(0, 0, w - 1, h - 1, 5, 5);
            }
        }

        /** Hull silhouette: the bow and stern cells get the pointed ends, middle cells the deck. */
        private void paintShip(Graphics2D g, int w, int h, int cx, int cy) {
            Ship ship = fleetCells[x][y];
            g.setColor(new Color(216, 226, 255, 235));
            if (ship == null) {
                g.fillRoundRect(w / 3, h / 6, w / 3, h * 2 / 3, 4, 4);
                g.setColor(new Color(238, 242, 255));
                g.drawLine(cx, h / 3, cx, h * 2 / 3);
                return;
            }
            List<Coordinate> cells = ship.getCoordinates();
            boolean horizontal = cells.get(0).getY() == cells.get(1).getY();
            int min = SIZE, max = -1;
            for (Coordinate cell : cells) {
                int value = horizontal ? cell.getX() : cell.getY();
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            int position = horizontal ? x : y;
            boolean rotated = !horizontal;
            if (rotated) {
                // a vertical ship is drawn as a rotated horizontal one, so the shape is identical
                g.translate(w, 0);
                g.rotate(Math.PI / 2);
                int swap = w;
                w = h;
                h = swap;
                cx = w / 2;
                cy = h / 2;
            }
            int top = h / 4, bottom = h * 3 / 4;
            int left = position == min ? w / 7 : 0;
            int right = position == max ? w * 6 / 7 : w;
            int bowX = position == min ? left + w / 4 : left;
            g.setColor(new Color(196, 182, 255));
            g.fillPolygon(new int[]{left, bowX, right, right, bowX}, new int[]{cy, top, top, bottom, bottom}, 5);
            g.setColor(new Color(122, 106, 196));
            int deckX = position == min ? w / 3 : 0;
            int deckWidth = position == max ? w * 2 / 3 : w;
            g.fillRect(deckX, h / 3, deckWidth, h / 3);
            if (position == min + (max - min) / 2) {
                g.setColor(new Color(240, 238, 255));
                g.fillRoundRect(w / 3, h / 3, w / 3, h / 3, 3, 3);
                g.setStroke(new BasicStroke(2));
                g.drawLine(w / 2, h / 2, w * 4 / 5, h / 2);
            }
            if (rotated) {
                g.rotate(-Math.PI / 2);
                g.translate(-h, 0);
            }
        }

        /** Hit cross, miss splashes, invalid preview and the selection ring. */
        private void paintMarks(Graphics2D g, CellState state, int w, int h, int cx, int cy, boolean selected) {
            if (state == CellState.HIT || state == CellState.SUNK) {
                g.setColor(Theme.DANGER);
                g.setStroke(new BasicStroke(2));
                int r = Math.max(4, w / 6);
                g.drawLine(cx - r, cy - r, cx + r, cy + r);
                g.drawLine(cx + r, cy - r, cx - r, cy + r);
                if (state == CellState.SUNK) {
                    g.drawOval(cx - r - 3, cy - r - 3, 2 * r + 6, 2 * r + 6);
                }
            }
            if (state == CellState.MISS) {
                g.setColor(new Color(226, 246, 255, 205));
                g.fillOval(cx - 2, cy - 2, 5, 5);
                g.setStroke(new BasicStroke(1.4f));
                for (int i = 0; i < 4; i++) {
                    double angle = Math.PI / 4 + i * Math.PI / 2;
                    g.drawLine((int) (cx + Math.cos(angle) * 4), (int) (cy + Math.sin(angle) * 4),
                            (int) (cx + Math.cos(angle) * 8), (int) (cy + Math.sin(angle) * 8));
                }
            }
            if (state == CellState.PREVIEW_INVALID) {
                g.setColor(Theme.DANGER);
                g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                g.drawLine(cx + 4, cy - 4, cx - 4, cy + 4);
            }
            if (selected || hasFocus()) {
                g.setColor(selected ? Theme.WARNING : Theme.PRIMARY);
                g.setStroke(new BasicStroke(2));
                g.drawRoundRect(1, 1, w - 3, h - 3, 5, 5);
                if (selected) {
                    g.drawOval(cx - 6, cy - 6, 12, 12);
                    g.drawLine(cx - 9, cy, cx + 9, cy);
                    g.drawLine(cx, cy - 9, cx, cy + 9);
                }
            }
        }
    }

    /** Glass tile with a wet hint; every cell of a board shares one size, so one tile is cached. */
    private BufferedImage waterTile(int w, int h) {
        if (waterTile != null && tileWidth == w && tileHeight == h) {
            return waterTile;
        }
        if (w < 4 || h < 4) {
            waterTile = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = Theme.graphics(waterTile);
            g.setColor(Theme.CELL_WATER);
            g.fillRect(0, 0, Math.max(1, w - 1), Math.max(1, h - 1));
            g.dispose();
            return waterTile;
        }
        BufferedImage tile = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Theme.graphics(tile);
        g.setPaint(new GradientPaint(0, 0, new Color(126, 236, 226, 58), (float) (w * .4), h, new Color(18, 68, 132, 94)));
        g.fillRoundRect(0, 0, w - 1, h - 1, 5, 5);
        g.setColor(new Color(214, 246, 255, 26));
        int waterY = h * 2 / 3;
        g.drawLine(w / 4, waterY, w / 2, waterY);
        g.drawLine(w / 2, waterY + 2, w * 3 / 4, waterY + 2);
        g.setColor(new Color(164, 224, 244, 46));
        g.drawRoundRect(0, 0, w - 1, h - 1, 5, 5);
        g.dispose();
        tileWidth = w;
        tileHeight = h;
        return waterTile = tile;
    }
}
