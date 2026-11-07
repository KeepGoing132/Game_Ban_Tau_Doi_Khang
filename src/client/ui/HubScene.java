package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

public class HubScene extends JPanel {
    public interface Entry {
        void open(int mode);
    }

    private final JButton[] plates = new JButton[3];
    private final int[] placedX = new int[3], placedY = new int[3], placedW = new int[3], placedH = new int[3];
    private final String[] titles = {"ĐẤU VỚI NGƯỜI", "ĐẤU VỚI MÁY", "XƯỞNG CHIẾN HẠM"};
    private final String[] subtitles = {"Tìm đối thủ · phòng chờ", "Ba mức Dễ · Thường · Khó", "Sơn màu · đặt tên hạm đội"};
    private final Color[] accents = {Theme.MINT, Theme.AMBER, Theme.ORCHID};
    private Entry entry;
    private int selected, hovered = -1;

    public HubScene() {
        setOpaque(false);
        setLayout(null);
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            JButton plate = new JButton(titles[i]);
            plate.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
            plate.setForeground(Theme.TEXT_MAIN);
            plate.setContentAreaFilled(false);
            plate.setOpaque(false);
            plate.setFocusPainted(false);
            plate.setBorderPainted(false);
            plate.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            plate.addActionListener(e -> {
                if (entry != null) {
                    entry.open(mode);
                }
            });
            plates[i] = plate;
            add(plate);
        }
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int hit = at(e.getX(), e.getY());
                if (hit >= 0 && entry != null) {
                    entry.open(hit);
                }
            }

            @Override public void mouseExited(MouseEvent e) {
                hovered = -1;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int hit = at(e.getX(), e.getY());
                if (hit != hovered) {
                    hovered = hit;
                    setCursor(hit >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }
        });
    }

    public void setEntry(Entry value) { entry = value; }

    @Override public Dimension getPreferredSize() { return new Dimension(320, 300); }
    @Override public Dimension getMinimumSize() { return new Dimension(200, 300); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 300); }

    public void setSelected(int mode) {
        selected = mode;
        for (int i = 0; i < plates.length; i++) {
            plates[i].setForeground(i == mode ? accents[i] : Theme.TEXT_MAIN);
        }
        repaint();
    }

    public JButton plate(int mode) { return plates[mode]; }

    private int at(int mx, int my) {
        for (int i = 0; i < 3; i++) {
            if (mx >= placedX[i] && mx <= placedX[i] + placedW[i] && my >= placedY[i] && my <= placedY[i] + placedH[i] + 30) {
                return i;
            }
        }
        return -1;
    }

    @Override public void doLayout() {
        place(getWidth(), getHeight());
        for (int i = 0; i < 3; i++) {
            plates[i].setBounds(placedX[i], placedY[i] + placedH[i] + 4, Math.max(90, placedW[i]), 24);
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        int w = getWidth(), h = getHeight();
        if (w < 60 || h < 60) {
            return;
        }
        Graphics2D g = Theme.graphics(graphics);
        place(w, h);
        g.setPaint(new GradientPaint(0, 0, new Color(122, 216, 236), 0, (float) (h * 0.5), new Color(40, 150, 190)));
        g.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
        g.setPaint(new GradientPaint(0, (float) (h * 0.40), new Color(22, 154, 186), 0, h, new Color(8, 78, 118)));
        g.fillRect(0, (int) (h * 0.40), w, h - (int) (h * 0.40));
        g.setColor(new Color(255, 255, 255, 40));
        for (int i = 0; i < 7; i++) {
            int y = (int) (h * (0.46 + i * 0.075));
            g.drawLine((int) (w * 0.02 + (i % 3) * 26), y, (int) (w * 0.72 - (i % 4) * 30), y);
        }
        int coast = (int) (h * 0.60);
        Path2D sand = new Path2D.Double();
        sand.moveTo(-10, coast + 44);
        sand.curveTo(w * 0.20, coast - 20, w * 0.36, coast + 18, w * 0.54, coast - 8);
        sand.curveTo(w * 0.72, coast - 30, w * 0.88, coast + 10, w + 10, coast - 14);
        sand.lineTo(w + 10, h + 10);
        sand.lineTo(-10, h + 10);
        sand.closePath();
        g.setPaint(new GradientPaint(0, coast - 20, new Color(238, 214, 156), 0, h, new Color(196, 166, 108)));
        g.fill(sand);
        g.setColor(new Color(255, 246, 214, 150));
        g.setStroke(new BasicStroke(2f));
        g.draw(sand);
        Path2D island = new Path2D.Double();
        island.moveTo(-10, coast + 26);
        island.curveTo(w * 0.22, coast - 34, w * 0.38, coast + 6, w * 0.56, coast - 20);
        island.curveTo(w * 0.74, coast - 42, w * 0.90, coast - 4, w + 10, coast - 26);
        island.lineTo(w + 10, h + 10);
        island.lineTo(-10, h + 10);
        island.closePath();
        g.setPaint(new GradientPaint(0, coast - 34, new Color(96, 196, 96), 0, h, new Color(38, 122, 62)));
        g.fill(island);
        for (int i = 0; i < 26; i++) {
            double bx = (i * 97 % 1000) / 1000.0 * w, by = coast + 14 + (i * 53 % 1000) / 1000.0 * (h - coast - 18);
            g.setColor(i % 2 == 0 ? new Color(126, 216, 116, 130) : new Color(54, 140, 74, 120));
            g.fillOval((int) bx, (int) by, 10 + i % 5 * 3, 7 + i % 4 * 3);
        }
        g.setColor(new Color(150, 106, 62));
        g.fillRect((int) (w * 0.05), (int) (h * 0.74), (int) (w * 0.12), 7);
        g.fillRect((int) (w * 0.82), (int) (h * 0.74), (int) (w * 0.12), 7);
        for (int i = 0; i < 3; i++) {
            boolean active = selected == i, over = hovered == i;
            double glow = active ? 1 : over ? .55 : 0;
            int plateY = placedY[i] + placedH[i] + 4, plateW = Math.max(90, placedW[i]), plateH = 24;
            g.setColor(new Color(6, 34, 44, 216));
            g.fillRoundRect(placedX[i], plateY, plateW, plateH, 10, 10);
            g.setColor(active ? accents[i] : new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(active ? 2f : 1f));
            g.drawRoundRect(placedX[i], plateY, plateW, plateH, 10, 10);
            HubArt.paint(g, i, placedX[i], placedY[i] - (over ? 4 : 0), placedW[i], placedH[i], glow);
            if (active) {
                g.setColor(accents[i]);
                g.setStroke(new BasicStroke(2.4f));
                g.draw(new RoundRectangle2D.Double(placedX[i] - 2, placedY[i] - 2, placedW[i] + 4, placedH[i] + 4, 14, 14));
            }
        }
        g.dispose();
    }

    private void place(int w, int h) {
        int unit = Math.max(150, (int) (w * 0.26));
        int tall = Math.max(96, (int) (h * 0.46));
        placedW[0] = unit; placedH[0] = tall;
        placedW[1] = (int) (unit * 0.86); placedH[1] = (int) (tall * 0.86);
        placedW[2] = (int) (unit * 1.02); placedH[2] = (int) (tall * 0.94);
        int gap = Math.max(16, (w - placedW[0] - placedW[1] - placedW[2] - 40) / 3);
        placedX[0] = 24;
        placedX[1] = placedX[0] + placedW[0] + gap;
        placedX[2] = placedX[1] + placedW[1] + gap;
        placedY[0] = (int) (h * 0.23);
        placedY[1] = (int) (h * 0.29);
        placedY[2] = (int) (h * 0.21);
    }
}
