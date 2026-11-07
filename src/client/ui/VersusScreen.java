package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;

public class VersusScreen extends JDialog {
    private static final int TICK_MS = 16;

    private final Stage stage;
    private final Timer clock;
    private final long totalMs;
    private final Runnable onDone;
    private long startedNanos;
    private boolean finished;

    public VersusScreen(Window owner, String myName, int myPoints, String opponentName, int opponentPoints,
                        String opponentTag, int fleetShips, int fleetCells, Runnable onDone) {
        super(owner, "Battleship • Trận đấu bắt đầu", ModalityType.MODELESS);
        setUndecorated(true);
        this.onDone = onDone;
        this.totalMs = durationMs();
        this.stage = new Stage(myName, myPoints, opponentName, opponentPoints, opponentTag, fleetShips, fleetCells);
        setContentPane(stage);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1180, screen.width), Math.min(720, screen.height));
        setLocation(screen.x + (screen.width - getWidth()) / 2, screen.y + (screen.height - getHeight()) / 2);
        clock = new Timer(TICK_MS, e -> tick());
        clock.setCoalesce(true);
        setFocusableWindowState(true);
        getRootPane().registerKeyboardAction(e -> finish(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> finish(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        stage.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { finish(); }
        });
        GameAudio.play(GameAudio.Sound.PING);
    }

    static int durationMs() {
        try {
            return Math.max(120, Integer.getInteger("battleship.vs_ms", 2600));
        } catch (RuntimeException e) {
            return 2600;
        }
    }

    @Override public void addNotify() {
        super.addNotify();
        startedNanos = System.nanoTime();
        clock.start();
        requestFocus();
    }

    @Override public void removeNotify() {
        clock.stop();
        super.removeNotify();
    }

    private void tick() {
        long elapsedNs = System.nanoTime() - startedNanos;
        double p = Math.min(1, elapsedNs / (totalMs * 1e6));
        stage.progress(p, remainingSeconds(elapsedNs / 1e6));
        if (p >= 1 || finished) {
            finish();
            return;
        }
        stage.repaint();
    }

    private int remainingSeconds(double elapsedMs) {
        double left = Math.max(0, totalMs - elapsedMs);
        return (int) Math.ceil(left / 1000.0);
    }

    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        clock.stop();
        dispose();
        if (onDone != null) {
            onDone.run();
        }
    }

    private static final class Stage extends JPanel {
        private final String myName, opponentName, opponentTag;
        private final int myPoints, opponentPoints, fleetShips, fleetCells;
        private double p;
        private int seconds = 3;

        Stage(String myName, int myPoints, String opponentName, int opponentPoints, String opponentTag, int fleetShips, int fleetCells) {
            this.myName = myName;
            this.myPoints = myPoints;
            this.opponentName = opponentName;
            this.opponentPoints = opponentPoints;
            this.opponentTag = opponentTag;
            this.fleetShips = fleetShips;
            this.fleetCells = fleetCells;
            setOpaque(true);
            setBackground(Theme.BG_DARK);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void progress(double value, int remaining) {
            p = value;
            seconds = remaining;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 40) {
                return;
            }
            Graphics2D g = Theme.graphics(graphics);
            OceanScene.paintBackdrop(g, w, h, OceanScene.isAnimated() ? OceanScene.time() : 0);
            g.setColor(new Color(4, 20, 30, 150));
            g.fillRect(0, 0, w, h);
            OceanScene.paintDrift(g, w, h, OceanScene.isAnimated() ? OceanScene.time() : 0);

            double slide = OceanScene.easeOutCubic(Math.min(1, p / 0.42));
            double cardW = Math.min(420, w * 0.33), cardH = Math.min(340, h * 0.62);
            double leftX = OceanScene.lerp(-cardW - 30, w * 0.5 - cardW - Math.max(70, w * 0.075), slide);
            double rightX = OceanScene.lerp(w + 30, w * 0.5 + Math.max(70, w * 0.075), slide);
            double cardY = h * 0.5 - cardH * 0.5;
            paintCard(g, leftX, cardY, cardW, cardH, myName, myPoints, "BẠN", Theme.PRIMARY, false);

            boolean machine = opponentTag != null && !opponentTag.isEmpty();
            paintCard(g, rightX, cardY, cardW, cardH, opponentName, opponentPoints,
                    machine ? opponentTag : "ĐỐI THỦ", machine ? Theme.AMBER : Theme.CORAL, true);

            double badgeP = OceanScene.clamp01((p - 0.30) / 0.28);
            paintFlash(g, w, h);
            paintClash(g, w * 0.5, h * 0.5, OceanScene.easeOutBack(badgeP), seconds);
            paintFooter(g, w, h);
            g.dispose();
        }

        private void paintFlash(Graphics2D g, int w, int h) {
            double strength = p < 0.045 ? p / 0.045 : 1 - OceanScene.clamp01((p - 0.045) / 0.075);
            if (strength <= 0) {
                return;
            }
            g.setColor(new Color(255, 255, 255, (int) (52 * strength)));
            g.fillRect(0, 0, w, h);
        }

        private void paintCard(Graphics2D g, double x, double y, double w, double h, String name, int points,
                               String caption, Color accent, boolean mirrored) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint((float) x, (float) y, new Color(10, 46, 61, 238), (float) x, (float) (y + h), new Color(4, 20, 30, 242)));
            g2.fill(new RoundRectangle2D.Double(x, y, w, h, 22, 22));
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(new RoundRectangle2D.Double(x, y, w, h, 22, 22));
            g2.setPaint(new GradientPaint((float) x, (float) y, new Color(255, 255, 255, 26), (float) x, (float) (y + h * 0.5), new Color(255, 255, 255, 0)));
            g2.fill(new RoundRectangle2D.Double(x + 1, y + 1, w - 2, h * 0.5, 21, 21));

            int centreX = (int) (x + w / 2);
            int insignia = (int) Math.min(112, w * 0.30);
            Rank.paintInsignia(g2, centreX - insignia / 2, (int) (y + h * 0.10), insignia, points);

            Rank.Tier tier = Rank.of(points);
            g2.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
            FontMetrics small = g2.getFontMetrics();
            String captionText = caption.toUpperCase(Locale.ROOT);
            g2.setColor(accent);
            g2.drawString(captionText, centreX - small.stringWidth(captionText) / 2, (int) (y + h * 0.10 + insignia + 26));

            g2.setFont(Theme.FONT_HEADER);
            FontMetrics header = g2.getFontMetrics();
            g2.setColor(Theme.TEXT_MAIN);
            g2.drawString(name, centreX - header.stringWidth(name) / 2, (int) (y + h * 0.62));

            g2.setFont(Theme.FONT_BOLD);
            FontMetrics bold = g2.getFontMetrics();
            String rankText = tier.name().toUpperCase(Locale.ROOT);
            g2.setColor(tier.color());
            g2.drawString(rankText, centreX - bold.stringWidth(rankText) / 2, (int) (y + h * 0.62) + 24);

            g2.setFont(Theme.FONT_SMALL);
            FontMetrics tiny = g2.getFontMetrics();
            String pointsText = points + " điểm";
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(pointsText, centreX - tiny.stringWidth(pointsText) / 2, (int) (y + h * 0.62) + 46);

            double barY = y + h - 34, barW = w - 60, barX = x + 30;
            g2.setColor(new Color(255, 255, 255, 20));
            g2.fill(new RoundRectangle2D.Double(barX, barY, barW, 7, 7, 7));
            g2.setColor(tier.color());
            g2.fill(new RoundRectangle2D.Double(barX, barY, Math.max(7, barW * Rank.progress(points)), 7, 7, 7));
            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.TEXT_MUTED);
            String progress = Rank.summary(points);
            g2.drawString(progress, (float) barX, (float) (barY - 8));
            if (mirrored) {
                g2.dispose();
                return;
            }
            g2.dispose();
        }

        private void paintClash(Graphics2D g, double cx, double cy, double amount, int seconds) {
            if (amount <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float radius = (float) (150 + 40 * amount);
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), radius, new float[] {0f, 1f},
                    new Color[] {new Color(22, 199, 192, (int) (90 * amount)), new Color(22, 199, 192, 0)}));
            g2.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
            g2.setStroke(new BasicStroke(6f));
            g2.setColor(new Color(99, 230, 222, (int) (200 * amount)));
            g2.draw(new Ellipse2D.Double(cx - 78, cy - 78, 156, 156));
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(232, 244, 248, (int) (120 * amount)));
            g2.draw(new Ellipse2D.Double(cx - 96, cy - 96, 192, 192));

            double scale = 0.62 + 0.5 * amount;
            g2.translate(cx, cy);
            g2.scale(scale, scale);
            g2.setFont(new Font(Theme.FONT_TITLE.getName(), Font.BOLD, 76));
            FontMetrics metrics = g2.getFontMetrics();
            String text = "VS";
            int tx = -metrics.stringWidth(text) / 2, ty = (metrics.getAscent() - metrics.getDescent()) / 2;
            g2.setColor(new Color(4, 20, 30, 220));
            g2.drawString(text, tx + 3, ty + 3);
            g2.setPaint(new GradientPaint(-40, -40, Theme.SEA_FOAM, 40, 40, Theme.PRIMARY));
            g2.drawString(text, tx, ty);
            g2.dispose();

            int countdown = Math.max(1, Math.min(3, seconds));
            g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            String second = String.valueOf(countdown);
            g2.setFont(Theme.FONT_TITLE.deriveFont(Font.BOLD, 22f));
            FontMetrics small = g2.getFontMetrics();
            g2.setColor(Theme.TEXT_MUTED);
            String hint = "Bắt đầu sau " + second + " • nhấn để vào ngay";
            g2.drawString(hint, (float) (cx - small.stringWidth(hint) / 2.0), (float) (cy + 208));
            g2.dispose();
        }

        private void paintFooter(Graphics2D g, int w, int h) {
            String fleet = fleetShips + " tàu • " + fleetCells + " ô • bố trí trong 120 giây";
            g.setFont(Theme.FONT_SMALL);
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(Theme.SEA_HORIZON);
            g.drawString("HẠM ĐỘI CHUẨN", (w - metrics.stringWidth("HẠM ĐỘI CHUẨN")) / 2, h - 74);
            g.setColor(Theme.TEXT_MAIN);
            Font bold = Theme.FONT_BOLD;
            g.setFont(bold);
            FontMetrics boldMetrics = g.getFontMetrics();
            g.drawString(fleet, (w - boldMetrics.stringWidth(fleet)) / 2, h - 50);
            g.setFont(Theme.FONT_SMALL);
            g.setColor(Theme.TEXT_MUTED);
            String hint = "Bắn trúng giữ lượt • trượt thì đổi lượt • đợi 15 giây là mất lượt";
            g.drawString(hint, (w - g.getFontMetrics().stringWidth(hint)) / 2, h - 26);
        }
    }

    static Path2D chevron(double cx, double cy, double width, double height) {
        Path2D path = new Path2D.Double();
        path.moveTo(cx - width / 2, cy + height);
        path.lineTo(cx, cy);
        path.lineTo(cx + width / 2, cy + height);
        return path;
    }
}
