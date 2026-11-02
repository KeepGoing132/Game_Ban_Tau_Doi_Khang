package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

public class SeaDecor extends JPanel {
    public enum Kind { COMPASS, SONAR, WAVES, HORIZON, DEPTH }
    private final Kind kind;

    public SeaDecor(Kind kind) { this(kind, new BorderLayout()); }
    public SeaDecor(Kind kind, LayoutManager layout) { super(layout); this.kind = kind; setOpaque(false); }

    @Override protected void paintComponent(Graphics graphics) {
        int w = getWidth(), h = getHeight();
        if (w < 6 || h < 6) return;
        Graphics2D g = Theme.graphics(graphics);
        double t = OceanScene.isAnimated() ? OceanScene.time() : 0;
        switch (kind) {
            case COMPASS:
                OceanScene.paintCompassRose(g, w / 2.0, h / 2.0, Math.min(w, h) * 0.42, t);
                break;
            case SONAR: {
                double r = Math.min(w, h) * 0.44, cx = w / 2.0, cy = h / 2.0;
                float span = (float) Math.max(w, h) * 0.75f;
                g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), span, new float[] {0f, 1f},
                        new Color[] {new Color(46, 136, 172, 96), new Color(6, 30, 56, 168)}));
                g.fill(new Ellipse2D.Double(cx - span, cy - span, span * 2, span * 2));
                g.setColor(new Color(126, 202, 226, 64)); g.setStroke(new BasicStroke(1f));
                for (int i = 1; i <= 3; i++) g.draw(new Ellipse2D.Double(cx - r * i / 3.0, cy - r * i / 3.0, r * 2 * i / 3.0, r * 2 * i / 3.0));
                g.draw(new Line2D.Double(cx - r, cy, cx + r, cy));
                g.draw(new Line2D.Double(cx, cy - r, cx, cy + r));
                OceanScene.paintSonar(g, cx, cy, r, t, 2.4, Theme.PRIMARY, 3, 0.55);
                int blips = 3;
                for (int i = 0; i < blips; i++) {
                    double phase = (t / 2.4 + i * 0.31) % 1.0, radius = r * (0.25 + noise(i * 7) * 0.7);
                    double angle = OceanScene.noise(i * 13) * Math.PI * 2 + t * 0.35;
                    double fade = Math.sin(Math.PI * phase);
                    double bx = cx + Math.cos(angle) * radius, by = cy + Math.sin(angle) * radius;
                    g.setColor(new Color(255, 214, 150, (int) (220 * fade)));
                    g.fill(new Ellipse2D.Double(bx - 2.4, by - 2.4, 4.8, 4.8));
                    g.setColor(new Color(255, 214, 150, (int) (110 * fade)));
                    g.draw(new Ellipse2D.Double(bx - 1 - radius * 0.16, by - 1 - radius * 0.16, 2 + radius * 0.32, 2 + radius * 0.32));
                }
                break;
            }
            case WAVES:
                OceanScene.paintWaveRule(g, 2, h / 2, w - 4, t, Theme.SEA_FOAM, 0.32);
                OceanScene.paintWaveRule(g, 2, h / 2 + 3, w - 4, t * 1.3 + 1, Theme.SEA_HORIZON, 0.22);
                break;
            case HORIZON:
                OceanScene.paintMiniSeascape(g, w, h, t);
                OceanScene.paintWaveRule(g, 0, h - 3, w, t, Theme.SEA_FOAM, 0.28);
                break;
            case DEPTH:
                paintDepth(g, w, h, t);
                break;
            default: break;
        }
        g.dispose();
    }
    private void paintDepth(Graphics2D g, int w, int h, double t) {
        int ticks = 9, x = Math.min(14, w / 2);
        g.setColor(new Color(126, 202, 226, 46));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(x, 4, x, h - 4));
        g.setFont(Theme.FONT_GRID.deriveFont(9f));
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i < ticks; i++) {
            int y = 4 + (h - 8) * i / (ticks - 1);
            g.setColor(new Color(126, 202, 226, i % 2 == 0 ? 96 : 56));
            g.draw(new Line2D.Double(x - 4, y, x + 4, y));
            if (i % 2 == 0) {
                String label = String.valueOf(i * 10);
                g.setColor(new Color(144, 214, 234, 130));
                if (x + 6 + metrics.stringWidth(label) < w) g.drawString(label, x + 6, y + 3);
            }
        }
        double lead = h * 0.5 + Math.sin(t * 0.5) * h * 0.22;
        g.setColor(new Color(226, 246, 255, 150));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {3f, 3f}, (float) (t * 12 % 6)));
        g.draw(new Line2D.Double(0, lead, w * 0.62, lead));
        g.setColor(new Color(255, 214, 150, 200));
        g.fill(new Ellipse2D.Double(w * 0.62 - 2.5, lead - 2.5, 5, 5));
    }
    private static double noise(int seed) { return OceanScene.noise(seed); }
}
