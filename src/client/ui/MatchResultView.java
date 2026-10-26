package client.ui;

import common.model.Ship;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Locale;

/**
 * End-of-match screen. A huge animated WIN / LOSE headline slams onto the deck over the living sea,
 * then the two charts are thrown open: your own waters with the enemy shots, and the enemy waters
 * with yours. Everything here is drawn from one timeline value, so the whole scene can be sampled
 * at any moment and the animation never blocks the buttons.
 */
public class MatchResultView extends OceanPanel {
    /** Headline letters land one by one, then the charts unfold. */
    private static final double UNFOLD_START = 1.55, UNFOLD_SECONDS = 1.15;
    private static final double FLASH_WINDOW = 0.55;
    private static final double SETTLE_SECONDS = 16.0;

    private final boolean won;
    private final String myName, oppName, reason;
    private int awarded;
    private final int myShots, myHits, oppShots;
    private JLabel pointsBadge;
    private final long durationSeconds;
    private final List<Ship> myFleet, enemyFleet;
    private final Headline headline = new Headline();
    private final UnfoldHost boardHost = new UnfoldHost();
    private final BoardGridPanel myChart, enemyChart;
    private final JButton btnRematch = Theme.button("Chơi lại (Rematch)", true);
    private final JButton btnExit = Theme.button("Về phòng chỉ huy", false);
    private final JLabel lblHint;
    private final long startNanos = System.nanoTime();
    private double unfold;
    private final Timer effectTimer;

    public MatchResultView(boolean won, String myName, String oppName, String reason, int awardedPoints,
                           int myShots, int myHits, int oppShots, long durationSeconds,
                           List<Ship> myFleet, BoardGridPanel myBoard, List<Ship> enemyFleet, BoardGridPanel enemyBoard) {
        super(new BorderLayout(0, 12), won ? "CHIẾN THẮNG" : "TRẬN ĐẤU KẾT THÚC",
                won ? "HẠM ĐỘI ĐỐI THỦ ĐÃ BỊ ĐÁNH CHÌM" : "HẸN GẶP LẠI Ở LẦN RA KHƠI SAU");
        this.won = won; this.myName = myName; this.oppName = oppName; this.reason = reason;
        this.awarded = awardedPoints; this.myShots = myShots; this.myHits = myHits; this.oppShots = oppShots;
        this.durationSeconds = durationSeconds; this.myFleet = myFleet; this.enemyFleet = enemyFleet;
        // the battle screen already played the cast-off effect; this screen opens on its own headline
        finishIntro();
        setBorder(new EmptyBorder(16, 20, 14, 20));

        Color from = won ? new Color(18, 66, 92) : new Color(48, 26, 72);
        Color to = won ? new Color(30, 46, 100) : new Color(20, 20, 56);
        JPanel banner = Theme.vividCard(from, to, 10);
        banner.setLayout(new BorderLayout(0, 4));
        banner.setPreferredSize(new Dimension(0, 186));
        banner.add(headline, BorderLayout.CENTER);
        banner.add(chips(), BorderLayout.SOUTH);
        add(banner, BorderLayout.NORTH);

        boardHost.setLayout(new GridLayout(1, 2, 14, 0));
        myChart = new BoardGridPanel("HẢI PHẬN CỦA BẠN", false);
        myChart.setFleet(myFleet);
        if (myBoard != null) myChart.mirrorStates(myBoard);
        enemyChart = new BoardGridPanel("HẢI PHẬN ĐỐI THỦ", false);
        enemyChart.setFleet(enemyFleet);
        if (enemyBoard != null) enemyChart.mirrorStates(enemyBoard);
        boardHost.add(chart(myChart, (oppShots > 0 ? "Đối thủ đã bắn " + oppShots + " phát vào hạm đội của bạn" : "Đối thủ chưa bắn phát nào") + "  •  " + shipSummary(myFleet), myFleet));
        boardHost.add(chart(enemyChart, (myShots > 0 ? "Bạn đã bắn " + myShots + " phát, " + myHits + " trúng đích" : "Bạn chưa bắn phát nào") + "  •  " + shipSummary(enemyFleet), enemyFleet));
        add(boardHost, BorderLayout.CENTER);

        lblHint = Theme.label(won ? "Xuất sắc, chỉ huy! Chơi thêm một ván, hay trở về phòng chỉ huy?" : "Đừng nản lòng. Chơi lại để phục thù, hay trở về phòng chỉ huy?", Theme.FONT_SMALL, new Color(206, 228, 242));
        JPanel footer = Theme.transparent(new BorderLayout(16, 0));
        JPanel legend = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 16, 4));
        // plain symbols only: the UI font has no glyphs for the outline marks
        legend.add(Theme.badge("×  TRÚNG ĐÍCH", Theme.CORAL));
        legend.add(Theme.badge("○  BẮN TRƯỢT", Theme.AZURE));
        legend.add(Theme.badge("●  ĐÃ CHÌM", Theme.ORCHID));
        legend.add(Theme.label("Vị trí tàu của đối thủ chỉ được công bố sau khi trận kết thúc.", Theme.FONT_SMALL, Theme.SEA_HORIZON));
        footer.add(legend, BorderLayout.CENTER);
        JPanel actions = Theme.transparent(new BorderLayout(14, 4));
        actions.add(lblHint, BorderLayout.NORTH);
        JPanel buttons = Theme.transparent(new GridLayout(1, 2, 12, 0));
        buttons.add(btnRematch); buttons.add(btnExit);
        actions.add(buttons, BorderLayout.CENTER);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        effectTimer = new Timer(26, e -> tick());
        effectTimer.setCoalesce(true);
    }

    public JButton rematchButton() { return btnRematch; }
    public JButton exitButton() { return btnExit; }
    public JLabel hintLabel() { return lblHint; }
    public void setHint(String text) { lblHint.setText(text); }
    /** The two after-action charts; used by the GUI tests. */
    public BoardGridPanel myChart() { return myChart; }
    public BoardGridPanel enemyChart() { return enemyChart; }
    /** Result facts shown on the banner; used by the GUI tests. */
    public boolean won() { return won; }
    public int awardedPoints() { return awarded; }
    public void setAwardedPoints(int value) {
        awarded=value;
        if (pointsBadge!=null) { pointsBadge.setText(String.format(Locale.ROOT,"%+d ĐIỂM",value)); pointsBadge.setForeground(value>=0?Theme.MINT:Theme.CORAL); }
    }
    public int myShots() { return myShots; }
    public int myHits() { return myHits; }
    /** True while the charts are still unfolding; used by the tests. */
    public boolean isUnfolding() { return unfold < 1; }

    private JPanel chips() {
        JPanel chips = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 12, 2));
        pointsBadge=Theme.badge(String.format(Locale.ROOT, "%+d ĐIỂM", awarded), awarded >= 0 ? Theme.MINT : Theme.CORAL);
        chips.add(pointsBadge);
        chips.add(Theme.badge(myShots + " PHÁT BẮN", Theme.AZURE));
        chips.add(Theme.badge("CHÍNH XÁC " + (myShots == 0 ? 0 : Math.round(100.0 * myHits / myShots)) + "%", Theme.AMBER));
        chips.add(Theme.badge(durationSeconds > 0 ? String.format(Locale.ROOT, "%d:%02d", durationSeconds / 60, durationSeconds % 60) : "—", Theme.GOLD));
        chips.add(Theme.badge(reasonText(reason), won ? Theme.PRIMARY : Theme.VIOLET));
        return chips;
    }

    /** One chart with its own caption, mirrored from the live board so every mark is kept. */
    private JPanel chart(BoardGridPanel grid, String caption, List<Ship> fleet) {
        JPanel wrap = Theme.transparent(new BorderLayout(0, 6));
        wrap.add(grid, BorderLayout.CENTER);
        JPanel foot = Theme.transparent(new BorderLayout());
        foot.add(Theme.label(caption, Theme.FONT_SMALL, Theme.SEA_HORIZON), BorderLayout.WEST);
        foot.add(Theme.label(fleet == null ? "Chưa công bố" : fleet.size() + " / 5 tàu", Theme.FONT_SMALL, Theme.SEA_FOAM), BorderLayout.EAST);
        wrap.add(foot, BorderLayout.SOUTH);
        return wrap;
    }

    private static String shipSummary(List<Ship> fleet) {
        int cells = 0;
        if (fleet != null) for (Ship ship : fleet) cells += ship.getCoordinates().size();
        return cells == 0 ? "hạm đội chưa được công bố" : cells + " / 17 ô tàu";
    }

    public static String reasonText(String reason) {
        if ("ALL_SHIPS_DESTROYED".equals(reason)) return "ĐÁNH CHÌM TOÀN BỘ HẠM ĐỘI";
        if ("SURRENDER".equals(reason)) return "MỘT BÊN RỜI TRẬN";
        if ("DISCONNECTED".equals(reason)) return "MẤT KẾT NỐI";
        return "KẾT QUẢ ĐÃ LƯU";
    }

    private double elapsed() { return (System.nanoTime() - startNanos) / 1e9; }

    private void tick() {
        double t = elapsed();
        if (unfold < 1 && t >= UNFOLD_START) unfold = Math.min(1, (t - UNFOLD_START) / UNFOLD_SECONDS);
        // once the charts are open the frame only carries confetti and rain, so it can slow down
        int delay = t < UNFOLD_START + UNFOLD_SECONDS + 0.6 ? 26 : 55;
        if (effectTimer.getDelay() != delay) effectTimer.setDelay(delay);
        repaint();
        if (t > SETTLE_SECONDS) effectTimer.stop();
    }

    @Override public void addNotify() { super.addNotify(); if (OceanScene.isAnimated()) effectTimer.start(); else unfold = 1; }
    @Override public void removeNotify() { effectTimer.stop(); super.removeNotify(); }
    @Override public void disposeOcean() { effectTimer.stop(); super.disposeOcean(); }

    // ---------------------------------------------------------------- painted effects
    @Override protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        if (getWidth() < 40 || getHeight() < 40) return;
        double t = elapsed();
        Graphics2D g = Theme.graphics(graphics);
        if (won) paintConfetti(g, t); else paintRain(g, t);
        double flash = t < UNFOLD_START ? 0 : 1 - OceanScene.clamp01((t - UNFOLD_START) / FLASH_WINDOW);
        if (flash > 0.001) {
            g.setColor(new Color(236, 252, 255, (int) (74 * flash)));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        g.dispose();
    }

    private void paintConfetti(Graphics2D g, double t) {
        int w = getWidth(), h = getHeight();
        Color[] palette = {Theme.GOLD, Theme.MINT, Theme.MAGENTA, Theme.AZURE, Theme.LIME, Theme.SEA_FOAM};
        for (int i = 0; i < 54; i++) {
            double delay = 0.55 + OceanScene.noise(i * 3) * 1.3, age = t - delay;
            if (age < 0) continue;
            double speed = 92 + OceanScene.noise(i + 7) * 170;
            double sway = Math.sin(age * (1.4 + OceanScene.noise(i + 23)) + i) * 26;
            double x = OceanScene.noise(i + 13) * w + sway * (1 + OceanScene.noise(i) * 0.6);
            double y = (age * speed + OceanScene.noise(i + 31) * h) % (h + 90) - 45;
            double spin = age * (2.2 + OceanScene.noise(i + 41) * 3.4) + i;
            Color color = palette[i % palette.length];
            int alpha = (int) (215 * Math.max(0, Math.min(1, (h - y) / 120.0)) * Math.max(0, Math.min(1, age * 2.2)));
            if (alpha <= 0) continue;
            Graphics2D piece = (Graphics2D) g.create();
            piece.translate(x, y); piece.rotate(spin);
            piece.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            if (i % 3 == 0) piece.fill(new Ellipse2D.Double(-3.2, -3.2, 6.4, 6.4));
            else piece.fill(new RoundRectangle2D.Double(-4.6, -2.0, 9.2, 4.0, 2, 2));
            piece.dispose();
        }
    }

    private void paintRain(Graphics2D g, double t) {
        int w = getWidth(), h = getHeight();
        g.setColor(new Color(6, 14, 34, 96));
        g.fillRect(0, 0, w, h);
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 70; i++) {
            double speed = 240 + OceanScene.noise(i * 5) * 320;
            double x = (OceanScene.noise(i + 11) * (w + 200) + t * speed * 0.32) % (w + 200) - 100;
            double y = (OceanScene.noise(i + 17) * (h + 120) + t * speed) % (h + 120) - 60;
            double length = 16 + OceanScene.noise(i + 29) * 26;
            g.setColor(new Color(186, 222, 248, (int) (34 + OceanScene.noise(i + 37) * 52)));
            g.draw(new Line2D.Double(x, y, x + length * 0.30, y + length));
        }
        OceanScene.paintBubbles(g, w, h + 30, h * 0.35, t * 0.7, 14, 0.16, 611);
    }

    /** The headline word, animated letter by letter over a burst of light. */
    private class Headline extends JComponent {
        @Override public Dimension getPreferredSize() { return new Dimension(460, 128); }
        @Override protected void paintComponent(Graphics graphics) {
            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 30) return;
            double t = elapsed();
            Graphics2D g = Theme.graphics(graphics);
            if (won) paintSunburst(g, w, h, t); else paintDim(g, w, h, t);
            String word = won ? "WIN" : "LOSE";
            float size = Math.min(102f, Math.max(34f, h * 0.66f));
            Font font = Theme.FONT_TITLE.deriveFont(Font.BOLD, size);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            int advance = metrics.stringWidth(word);
            int left = (w - advance) / 2;
            int baseline = (h + metrics.getAscent()) / 2 - metrics.getDescent() + 4;
            Color top = won ? Theme.GOLD : Theme.CORAL;
            Color bottom = won ? Theme.MINT : Theme.VIOLET;
            double shimmer = t > 1.3 ? (t * 0.55 % 1.0) : -1;
            g.setColor(new Color(4, 14, 30, (int) (170 * Math.max(0, Math.min(1, (t - 0.35) / 0.5)))));
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < word.length(); i++) {
                double appear = OceanScene.clamp01((t - 0.10 - i * 0.11) / 0.62);
                if (appear <= 0) continue;
                double eased = OceanScene.easeOutBack(appear);
                String letter = word.substring(i, i + 1);
                int letterWidth = metrics.stringWidth(letter);
                int x = left + metrics.stringWidth(word.substring(0, i));
                Graphics2D gl = (Graphics2D) g.create();
                double scale = Math.max(0.2, Math.min(1.18, eased));
                gl.translate(x + letterWidth / 2.0, baseline - (1 - appear) * 96);
                gl.rotate((1 - appear) * -0.30);
                gl.scale(scale, scale);
                // dark drop shadow, then a soft glow, then the gradient face
                gl.setColor(new Color(3, 12, 28, (int) (200 * appear)));
                gl.drawString(letter, -letterWidth / 2 + 3, 3);
                gl.setColor(new Color(top.getRed(), top.getGreen(), top.getBlue(), (int) (70 * appear)));
                gl.setFont(font.deriveFont(Font.BOLD, size * 1.045f));
                FontMetrics glowMetrics = gl.getFontMetrics();
                gl.drawString(letter, -glowMetrics.stringWidth(letter) / 2, 0);
                gl.setFont(font);
                gl.setPaint(new GradientPaint(0, -metrics.getAscent(), top, 0, metrics.getDescent(), bottom));
                gl.drawString(letter, -letterWidth / 2, 0);
                if (shimmer >= 0 && shimmer > i / (double) word.length() - 0.12 && shimmer < i / (double) word.length() + 0.26) {
                    Shape clip = gl.getClip();
                    gl.setColor(new Color(255, 255, 255, 120));
                    gl.drawString(letter, -letterWidth / 2, 0);
                    gl.setClip(clip);
                }
                gl.dispose();
            }
            // subtitle under the word
            if (t > 0.75) {
                double appear = OceanScene.clamp01((t - 0.75) / 0.5);
                Font subFont = Theme.FONT_HEADER.deriveFont(Font.BOLD, Math.max(13f, size * 0.20f));
                g.setFont(subFont);
                FontMetrics sub = g.getFontMetrics();
                String text = won ? "CHIẾN THẮNG" : "THẤT BẠI";
                String detail = won ? "Bạn đã làm chủ vùng biển này" : "Hạm đội của bạn đã bị đánh chìm";
                int textWidth = sub.stringWidth(text);
                g.setColor(new Color(4, 18, 36, (int) (200 * appear)));
                g.drawString(text, (w - textWidth) / 2 + 1, h - 22 + 1);
                g.setColor(new Color(226, 246, 255, (int) (240 * appear)));
                g.drawString(text, (w - textWidth) / 2, h - 22);
                int detailWidth = sub.stringWidth(detail);
                g.setFont(Theme.FONT_SMALL);
                g.setColor(new Color(206, 228, 242, (int) (215 * appear)));
                g.drawString(detail, (w - detailWidth) / 2, h - 8);
                OceanScene.paintWaveRule(g, (w - textWidth) / 2 - 12, h - 30, textWidth + 24, t, Theme.SEA_FOAM, 0.35 * appear);
            }
            g.dispose();
        }
        /** Rotating rays behind a victory. */
        private void paintSunburst(Graphics2D g, int w, int h, double t) {
            double appear = OceanScene.clamp01((t - 0.05) / 0.9);
            double cx = w / 2.0, cy = h / 2.0, radius = Math.max(w, h) * 0.72;
            for (int i = 0; i < 22; i++) {
                double angle = i * Math.PI / 11 + t * 0.10;
                double spread = 0.028 + OceanScene.noise(i) * 0.030;
                Path2D ray = new Path2D.Double();
                ray.moveTo(cx, cy);
                ray.lineTo(cx + Math.cos(angle - spread) * radius, cy + Math.sin(angle - spread) * radius);
                ray.lineTo(cx + Math.cos(angle + spread) * radius, cy + Math.sin(angle + spread) * radius);
                ray.closePath();
                int alpha = (int) (16 * appear * (0.5 + 0.5 * Math.sin(t * 0.8 + i)));
                g.setColor(new Color(255, 226, 150, Math.max(0, alpha)));
                g.fill(ray);
            }
            OceanScene.paintSonar(g, cx, cy, Math.max(w, h) * 0.42, t - 0.1, 1.5, Theme.MINT, 3, 0.22 * (1 - OceanScene.clamp01(t / 4.0)));
            paintSailingShip(g, w, h, t, appear);
        }
        /** The winning hull sailing out of the burst. */
        private void paintSailingShip(Graphics2D g, int w, int h, double t, double appear) {
            if (appear < 1) return;
            double sail = OceanScene.clamp01((t - 1.0) / 2.4);
            double x = -40 + sail * (w + 80), y = h - 12 + Math.sin(t * 2.1) * 1.8;
            OceanScene.paintShipSilhouette(g, x, y, 0.62, new Color(8, 30, 52, 215), new Color(255, 216, 150, 190));
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(226, 246, 255, 120 - i * 16));
                g.fill(new Ellipse2D.Double(x - 12 - i * 6, y + 3 + Math.sin(t * 2 + i) * 1.2, 4 - i * 0.4, 2));
            }
        }
        /** A dimming sea, for the lost match. */
        private void paintDim(Graphics2D g, int w, int h, double t) {
            double appear = OceanScene.clamp01((t - 0.05) / 1.0);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, h / 2.0), Math.max(w, h) * 0.62f,
                    new float[]{0f, 1f}, new Color[]{new Color(4, 12, 30, (int) (30 * appear)), new Color(2, 8, 22, (int) (196 * appear))}));
            g.fillRect(0, 0, w, h);
            OceanScene.paintBubbles(g, w, h + 40, h * 0.4, t * 0.8, 22, 0.20, 733);
            double tilt = -0.22 - Math.min(0.20, t * 0.05), sink = OceanScene.clamp01((t - 1.0) / 3.4) * 12;
            Graphics2D wreck = (Graphics2D) g.create();
            wreck.translate(w * 0.5, h - 6 + sink);
            wreck.rotate(tilt);
            OceanScene.paintShipSilhouette(wreck, 0, 0, 0.60, new Color(10, 20, 40, 225), new Color(150, 120, 170, 150));
            wreck.dispose();
            OceanScene.paintWaveRule(g, 12, h - 9, w - 24, t * 0.8, new Color(150, 190, 226), 0.30 * appear);
        }
    }

    /** Holds the two charts and throws them open line by line. */
    private class UnfoldHost extends JPanel {
        UnfoldHost() { super(new GridLayout(1, 2, 14, 0)); setOpaque(false); }
        @Override public void paint(Graphics graphics) {
            if (unfold >= 1) { super.paint(graphics); return; }
            Graphics2D g = (Graphics2D) graphics.create();
            int w = getWidth(), h = getHeight();
            double eased = Math.min(1, OceanScene.easeOutBack(unfold));
            int half = (int) (h / 2.0 * eased);
            g.setClip(new Rectangle2D.Double(0, h / 2.0 - half, w, half * 2));
            g.translate(0, (1 - eased) * 20);
            super.paint(g);
            g.setClip(null);
            OceanScene.paintWaveRule(g, 6, h / 2 - half, w - 12, elapsed() * 1.2, Theme.SEA_FOAM, 0.55);
            OceanScene.paintWaveRule(g, 6, h / 2 + half, w - 12, elapsed() * 1.2 + 2, Theme.SEA_HORIZON, 0.45);
            g.dispose();
        }
    }
}
