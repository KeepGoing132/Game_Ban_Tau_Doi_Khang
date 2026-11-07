package client.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

public final class HubArt {
    private HubArt() { }

    public static void paint(Graphics2D g, int kind, int x, int y, int w, int h, double glow) {
        if (w < 20 || h < 20) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create(x, y, w, h);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.clip(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
        Color sky = kind == 0 ? new Color(24, 74, 88) : kind == 1 ? new Color(18, 62, 84) : new Color(26, 84, 92);
        g2.setPaint(new GradientPaint(0, 0, Theme.blend(sky, Color.WHITE, .10f), 0, h, new Color(5, 24, 34)));
        g2.fillRect(0, 0, w, h);
        int water = (int) (h * 0.70);
        g2.setPaint(new GradientPaint(0, water, new Color(14, 92, 108), 0, h, new Color(4, 26, 38)));
        g2.fillRect(0, water, w, h - water);
        g2.setColor(new Color(120, 210, 226, 60));
        for (int i = 0; i < 3; i++) {
            g2.drawLine(0, water + 6 + i * 7, w, water + 6 + i * 7);
        }
        g2.setColor(new Color(99, 230, 222, 120));
        g2.drawLine(0, water, w, water);
        switch (kind) {
            case 0: paintArena(g2, w, h, water); break;
            case 1: paintRange(g2, w, h, water); break;
            default: paintShipyard(g2, w, h, water); break;
        }
        if (glow > 0) {
            g2.setPaint(new GradientPaint(0, 0, new Color(99, 230, 222, (int) (70 * glow)), 0, h, new Color(99, 230, 222, 0)));
            g2.fillRect(0, 0, w, h);
        }
        g2.dispose();
    }

    private static void paintArena(Graphics2D g, int w, int h, int water) {
        int baseY = water + 4;
        int stadiumWidth = (int) (w * 0.66), stadiumX = (w - stadiumWidth) / 2;
        int stadiumHeight = (int) (h * 0.34);
        g.setColor(new Color(6, 28, 40, 190));
        g.fillRoundRect(stadiumX + 4, baseY - stadiumHeight + 6, stadiumWidth, stadiumHeight, 14, 14);
        Path2D dome = new Path2D.Double();
        dome.moveTo(stadiumX, baseY);
        dome.lineTo(stadiumX, baseY - stadiumHeight * 0.45);
        dome.curveTo(stadiumX + stadiumWidth * 0.18, baseY - stadiumHeight * 1.18,
                stadiumX + stadiumWidth * 0.82, baseY - stadiumHeight * 1.18, stadiumX + stadiumWidth, baseY - stadiumHeight * 0.45);
        dome.lineTo(stadiumX + stadiumWidth, baseY);
        dome.closePath();
        g.setPaint(new GradientPaint(0, baseY - stadiumHeight, new Color(120, 188, 208), 0, baseY, new Color(38, 92, 116)));
        g.fill(dome);
        g.setColor(new Color(232, 244, 248, 200));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(dome);
        g.setColor(new Color(255, 194, 75, 220));
        for (int i = 0; i < 5; i++) {
            int bx = stadiumX + 10 + i * (stadiumWidth - 20) / 4;
            g.fillRect(bx, baseY - (int) (stadiumHeight * 0.55), 5, (int) (stadiumHeight * 0.55));
        }
        g.setColor(new Color(22, 199, 192, 210));
        g.fillOval(w / 2 - 9, baseY - stadiumHeight - 12, 18, 18);
        g.setColor(new Color(255, 255, 255, 160));
        g.fillOval(w / 2 - 3, baseY - stadiumHeight - 6, 6, 6);
        g.setColor(new Color(226, 240, 248, 220));
        g.setStroke(new BasicStroke(2.4f));
        g.drawLine((int) (w * 0.16), (int) (h * 0.24), (int) (w * 0.30), (int) (h * 0.46));
        g.drawLine((int) (w * 0.30), (int) (h * 0.24), (int) (w * 0.16), (int) (h * 0.46));
        g.drawLine((int) (w * 0.84), (int) (h * 0.24), (int) (w * 0.70), (int) (h * 0.46));
        g.drawLine((int) (w * 0.70), (int) (h * 0.24), (int) (w * 0.84), (int) (h * 0.46));
    }

    private static void paintShipyard(Graphics2D g, int w, int h, int water) {
        int baseY = water + 6;
        g.setColor(new Color(6, 28, 40, 170));
        g.fillRect((int) (w * 0.10), baseY - 8, (int) (w * 0.80), 10);
        g.setColor(new Color(255, 194, 75, 200));
        for (int i = 0; i < 4; i++) {
            g.fillRect((int) (w * 0.16 + i * w * 0.18), baseY - 16, 8, 8);
        }
        int hullX = (int) (w * 0.20), hullW = (int) (w * 0.54), hullY = baseY - 26;
        int[] hullXs = {hullX, hullX + 16, hullX + hullW, hullX + hullW, hullX + 16};
        int[] hullYs = {hullY + 8, hullY - 6, hullY - 6, hullY + 8, hullY + 8};
        g.setColor(new Color(3, 16, 24, 150));
        g.fillPolygon(new int[]{hullXs[0], hullXs[1], hullXs[2], hullXs[3], hullXs[4]},
                new int[]{hullYs[0] + 3, hullYs[1] + 3, hullYs[2] + 3, hullYs[3] + 3, hullYs[4] + 3}, 5);
        g.setColor(new Color(226, 240, 248));
        g.fillPolygon(hullXs, hullYs, 5);
        g.setColor(new Color(120, 152, 174));
        g.setStroke(new BasicStroke(1.2f));
        g.drawPolygon(hullXs, hullYs, 5);
        g.setColor(new Color(126, 158, 180));
        g.fillRect(hullX + 20, hullY - 2, hullW - 40, 4);
        g.setColor(new Color(255, 194, 75));
        g.fillRect((int) (w * 0.34), hullY - 6, 10, 10);
        g.setColor(new Color(150, 176, 196));
        g.setStroke(new BasicStroke(3f));
        g.drawLine((int) (w * 0.30), (int) (h * 0.16), (int) (w * 0.30), baseY - 30);
        g.drawLine((int) (w * 0.30), (int) (h * 0.16), (int) (w * 0.72), (int) (h * 0.16));
        g.setStroke(new BasicStroke(1.6f));
        g.drawLine((int) (w * 0.66), (int) (h * 0.16), (int) (w * 0.66), hullY - 8);
        g.setColor(new Color(255, 236, 180, 230));
        for (int i = 0; i < 4; i++) {
            int sx = (int) (w * (0.60 + i * 0.03)), sy = (int) (h * (0.52 + (i % 2) * 0.05));
            g.fillOval(sx, sy, 3, 3);
        }
    }

    private static void paintRange(Graphics2D g, int w, int h, int water) {
        int baseY = water + 4;
        int[] heights = {(int) (h * 0.26), (int) (h * 0.34), (int) (h * 0.42)};
        Color[] tones = {new Color(120, 186, 206), new Color(88, 152, 176), new Color(62, 118, 142)};
        for (int i = 0; i < 3; i++) {
            int cx = (int) (w * (0.68 + i * 0.11));
            int ry = baseY - heights[i];
            g.setColor(new Color(6, 28, 40, 160));
            g.fillRoundRect(cx - 15, ry, 30, heights[i], 4, 4);
            g.setColor(tones[i]);
            g.fillRoundRect(cx - 13, ry + 2, 26, heights[i] - 2, 4, 4);
            g.setColor(new Color(22, 199, 192, 220));
            g.drawOval(cx - 9, ry + 8, 18, 18);
            g.setColor(new Color(255, 90, 60, 220));
            g.fillOval(cx - 4, ry + 13, 8, 8);
        }
        g.setColor(new Color(226, 240, 248, 230));
        g.setStroke(new BasicStroke(5f));
        int bx = (int) (w * 0.10), by = (int) (h * 0.52);
        g.drawLine(bx, by, (int) (w * 0.34), (int) (h * 0.36));
        g.setColor(new Color(120, 152, 174));
        g.fillOval(bx - 12, by - 6, 26, 20);
        g.fillOval(bx - 6, by + 8, 16, 8);
        g.setColor(new Color(22, 199, 192, 200));
        g.setStroke(new BasicStroke(1.4f));
        for (int i = 0; i < 3; i++) {
            int arc = 26 + i * 16;
            g.drawArc(bx - arc / 2, by - arc / 2, arc, arc, -46, 60);
        }
    }
}
