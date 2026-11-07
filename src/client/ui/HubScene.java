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
        g.setPaint(new GradientPaint(0, 0, new Color(9, 40, 54), 0, h, new Color(4, 20, 30)));
        g.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
        int coast = (int) (h * 0.66);
        Path2D land = new Path2D.Double();
        land.moveTo(0, coast + 30);
        land.curveTo(w * 0.22, coast - 26, w * 0.38, coast + 12, w * 0.55, coast - 14);
        land.curveTo(w * 0.72, coast - 34, w * 0.86, coast + 6, w, coast - 18);
        land.lineTo(w, h);
        land.lineTo(0, h);
        land.closePath();
        g.setPaint(new GradientPaint(0, coast - 30, new Color(24, 92, 96), 0, h, new Color(8, 40, 44)));
        g.fill(land);
        g.setColor(new Color(99, 230, 222, 120));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(land);
        for (int i = 0; i < 5; i++) {
            int y = coast + 28 + i * 14;
            g.setColor(new Color(140, 232, 226, 34));
            g.drawLine(18 + i * 9, y, w - 30 - i * 7, y);
        }
        int pierY = coast + 16;
        g.setColor(new Color(140, 160, 176));
        g.fillRect((int) (w * 0.06), pierY, (int) (w * 0.10), 7);
        g.fillRect((int) (w * 0.84), pierY, (int) (w * 0.10), 7);
        for (int i = 0; i < 3; i++) {
            boolean active = selected == i, over = hovered == i;
            double glow = active ? 1 : over ? .55 : 0;
            HubArt.paint(g, i, placedX[i], placedY[i] - (over ? 4 : 0), placedW[i], placedH[i], glow);
            if (active) {
                g.setColor(accents[i]);
                g.setStroke(new BasicStroke(2f));
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
        placedY[0] = (int) (h * 0.10);
        placedY[1] = (int) (h * 0.20);
        placedY[2] = (int) (h * 0.08);
    }
}
