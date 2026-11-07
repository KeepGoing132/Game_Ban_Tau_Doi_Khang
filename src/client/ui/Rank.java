package client.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Locale;

public final class Rank {
    private Rank() { }

    public static final class Tier {
        private final String name;
        private final int points;
        private final Color color;
        Tier(String name, int points, Color color) { this.name = name; this.points = points; this.color = color; }
        public String name() { return name; }
        public int points() { return points; }
        public Color color() { return color; }
        public int index() { return indexOf(this); }
    }

    private static final Tier[] TIERS = {
            new Tier("Thủy thủ", 0, new Color(127, 163, 176)),
            new Tier("Hạ sĩ", 6, new Color(79, 179, 217)),
            new Tier("Trung úy", 12, new Color(22, 199, 192)),
            new Tier("Thượng úy", 21, new Color(53, 208, 127)),
            new Tier("Hạm trưởng", 33, new Color(255, 194, 75)),
            new Tier("Đô đốc", 48, new Color(255, 90, 60))
    };

    public static Tier[] tiers() { return TIERS.clone(); }

    private static int indexOf(Tier tier) {
        for (int i = 0; i < TIERS.length; i++) if (TIERS[i] == tier) return i;
        return 0;
    }

    public static Tier of(int points) {
        Tier found = TIERS[0];
        for (Tier tier : TIERS) if (points >= tier.points()) found = tier;
        return found;
    }

    public static Tier next(int points) {
        for (Tier tier : TIERS) if (points < tier.points()) return tier;
        return null;
    }

    public static double progress(int points) {
        Tier current = of(points), next = next(points);
        if (next == null) return 1;
        int span = next.points() - current.points();
        return span <= 0 ? 1 : Math.max(0, Math.min(1, (points - current.points()) / (double) span));
    }

    public static String summary(int points) {
        Tier next = next(points);
        if (next == null) return "Cấp cao nhất · " + points + " điểm";
        return (next.points() - points) + " điểm nữa để lên " + next.name();
    }

    public static String shortLabel(int points) {
        Tier tier = of(points);
        return tier.name().toUpperCase(Locale.ROOT) + " • " + points + " điểm";
    }

    public static void paintInsignia(Graphics2D g, int x, int y, int size, int points) {
        Tier tier = of(points);
        int level = tier.index();
        Graphics2D g2 = Theme.graphics(g);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double cx = x + size / 2.0, cy = y + size / 2.0;
        double radius = size * 0.46;
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) Math.max(2, radius * 1.35),
                new float[] {0f, 1f}, new Color[] {Theme.blend(tier.color(), Theme.BG_DARK, .72f),
                new Color(Theme.BG_DARK.getRed(), Theme.BG_DARK.getGreen(), Theme.BG_DARK.getBlue(), 40)}));
        g2.fill(new java.awt.geom.Ellipse2D.Double(cx - radius * 1.3, cy - radius * 1.3, radius * 2.6, radius * 2.6));
        g2.setColor(Theme.blend(tier.color(), Theme.BG_DARK, .35f));
        g2.fill(new java.awt.geom.Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
        g2.setColor(tier.color());
        g2.setStroke(new BasicStroke(Math.max(1.4f, size / 22f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new java.awt.geom.Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
        int chevrons = Math.min(3, 1 + level / 2);
        double chevronWidth = radius * 0.86, chevronHeight = radius * 0.30, gap = radius * 0.42;
        double top = cy - (chevrons - 1) * gap / 2.0 + (level >= 3 ? radius * 0.18 : 0);
        for (int i = 0; i < chevrons; i++) {
            double vy = top + i * gap;
            Path2D chevron = new Path2D.Double();
            chevron.moveTo(cx - chevronWidth / 2, vy + chevronHeight);
            chevron.lineTo(cx, vy);
            chevron.lineTo(cx + chevronWidth / 2, vy + chevronHeight);
            g2.draw(chevron);
        }
        int stars = level <= 3 ? (level == 0 ? 0 : level) : 1;
        if (level >= 4) stars = level - 3;
        double starY = cy + radius * 0.52;
        for (int i = 0; i < stars; i++) {
            double sx = cx + (i - (stars - 1) / 2.0) * radius * 0.46;
            paintStar(g2, sx, starY, radius * 0.20);
        }
        g2.dispose();
    }

    private static void paintStar(Graphics2D g, double cx, double cy, double radius) {
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double r = i % 2 == 0 ? radius : radius * 0.44;
            double px = cx + Math.cos(angle) * r, py = cy + Math.sin(angle) * r;
            if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.closePath();
        g.fill(star);
    }
}
