package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

public final class NavalArt extends JComponent {
    private static final int ART_WIDTH = 440;
    private static final int ART_HEIGHT = 340;

    public NavalArt() {
        setOpaque(false);
        setPreferredSize(new Dimension(390, 320));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = Theme.graphics(graphics);
        double scale = Math.min(getWidth() / (double) ART_WIDTH, getHeight() / (double) ART_HEIGHT);
        g.translate((getWidth() - ART_WIDTH * scale) / 2, (getHeight() - ART_HEIGHT * scale) / 2);
        g.scale(scale, scale);

        paintChartGrid(g);
        int cx = 220, cy = 164;
        paintRangeRings(g, cx, cy);
        paintSweep(g, cx, cy);
        paintHull(g, cx, cy);
        paintContacts(g);
        g.dispose();
    }

    private void paintChartGrid(Graphics2D g) {
        g.setColor(new Color(24, 47, 61));
        for (int x = 10; x < ART_WIDTH; x += 26) {
            g.drawLine(x, 0, x, ART_HEIGHT);
        }
        for (int y = 0; y < ART_HEIGHT; y += 26) {
            g.drawLine(10, y, 430, y);
        }
    }

    private void paintRangeRings(Graphics2D g, int cx, int cy) {
        for (int radius = 50; radius <= 150; radius += 50) {
            g.setColor(new Color(42, 79, 89));
            g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        }
    }

    private void paintSweep(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(29, 71, 73));
        g.fill(new Arc2D.Double(70, 14, 300, 300, 22, 37, Arc2D.PIE));
        g.setColor(Theme.PRIMARY);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Arc2D.Double(70, 14, 300, 300, 22, 37, Arc2D.OPEN));
        g.setColor(new Color(51, 86, 97));
        g.drawLine(55, cy, 385, cy);
        g.drawLine(cx, 0, cx, 329);
    }

    private void paintHull(Graphics2D g, int cx, int cy) {
        g.rotate(-.54, cx, cy);
        Path2D hull = new Path2D.Double();
        hull.moveTo(220, 42);
        hull.curveTo(199, 72, 193, 90, 193, 118);
        hull.lineTo(196, 264);
        hull.lineTo(244, 264);
        hull.lineTo(247, 118);
        hull.curveTo(247, 90, 241, 72, 220, 42);
        hull.closePath();
        g.setColor(new Color(21, 56, 67));
        g.fill(hull);
        g.setColor(Theme.PRIMARY);
        g.setStroke(new BasicStroke(2));
        g.draw(hull);

        g.setColor(new Color(66, 126, 137));
        g.fillRoundRect(203, 132, 34, 68, 5, 5);
        g.setColor(new Color(155, 204, 202));
        g.drawRoundRect(203, 132, 34, 68, 5, 5);
        g.drawRect(208, 144, 24, 28);
        g.drawLine(220, 136, 220, 185);
        g.drawLine(209, 151, 231, 151);

        for (int y : new int[]{102, 220}) {
            g.setColor(new Color(42, 88, 103));
            g.fillOval(210, y, 20, 20);
            g.setColor(Theme.PRIMARY);
            g.drawOval(210, y, 20, 20);
            g.drawLine(217, y + 4, 217, y - 14);
            g.drawLine(223, y + 4, 223, y - 14);
        }
        g.setColor(new Color(51, 86, 97));
        g.drawLine(201, 273, 188, 306);
        g.drawLine(220, 275, 220, 320);
        g.drawLine(239, 273, 252, 306);
        g.rotate(.54, cx, cy);
    }

    private void paintContacts(Graphics2D g) {
        for (int[] contact : new int[][]{{104, 103}, {340, 197}, {315, 65}}) {
            g.setColor(new Color(48, 88, 89));
            g.drawOval(contact[0] - 9, contact[1] - 9, 18, 18);
            g.setColor(Theme.PRIMARY);
            g.fillOval(contact[0] - 3, contact[1] - 3, 6, 6);
        }
    }
}
