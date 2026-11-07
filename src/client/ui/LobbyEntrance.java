package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

public class LobbyEntrance extends JDialog {
    private static final int TICK_MS = 16;

    private final Stage stage;
    private final Timer clock;
    private final long totalMs;
    private final Runnable onDone;
    private long startedNanos;
    private boolean finished;

    public LobbyEntrance(Window owner, String commander, int points, String channel, Runnable onDone) {
        super(owner, "Battleship • Vào sảnh chờ", ModalityType.MODELESS);
        setUndecorated(true);
        this.onDone = onDone;
        this.totalMs = durationMs();
        this.stage = new Stage(commander, points, channel);
        setContentPane(stage);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(880, screen.width), Math.min(520, screen.height));
        setLocation(screen.x + (screen.width - getWidth()) / 2, screen.y + (screen.height - getHeight()) / 2);
        clock = new Timer(TICK_MS, e -> tick());
        clock.setCoalesce(true);
        getRootPane().registerKeyboardAction(e -> finish(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> finish(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        stage.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { finish(); }
        });
        GameAudio.play(GameAudio.Sound.TURN);
    }

    static int durationMs() {
        try {
            return Math.max(120, Integer.getInteger("battleship.entrance_ms", 1600));
        } catch (RuntimeException e) {
            return 1600;
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
        double p = Math.min(1, (System.nanoTime() - startedNanos) / (totalMs * 1e6));
        stage.progress(p);
        if (p >= 1 || finished) {
            finish();
            return;
        }
        stage.repaint();
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
        private final String commander, channel;
        private final int points;
        private double p;

        Stage(String commander, int points, String channel) {
            this.commander = commander;
            this.points = points;
            this.channel = channel;
            setOpaque(true);
            setBackground(Theme.BG_DARK);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void progress(double value) { p = value; }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 40) {
                return;
            }
            Graphics2D g = Theme.graphics(graphics);
            OceanScene.paintBackdrop(g, w, h, OceanScene.isAnimated() ? OceanScene.time() : 0);
            g.setColor(new Color(4, 20, 30, 140));
            g.fillRect(0, 0, w, h);
            OceanScene.paintDrift(g, w, h, OceanScene.isAnimated() ? OceanScene.time() : 0);

            double appear = OceanScene.easeOutCubic(Math.min(1, p / 0.35));
            double cardW = Math.min(560, w * 0.72), cardH = Math.min(320, h * 0.72);
            double x = (w - cardW) / 2, y = (h - cardH) / 2 + (1 - appear) * 34;
            Graphics2D g2 = Theme.graphics(g);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, appear))));
            g2.setPaint(new GradientPaint(0, (float) y, new Color(10, 46, 61, 244), 0, (float) (y + cardH), new Color(4, 20, 30, 248)));
            g2.fill(new RoundRectangle2D.Double(x, y, cardW, cardH, 24, 24));
            g2.setColor(new Color(Theme.PRIMARY.getRed(), Theme.PRIMARY.getGreen(), Theme.PRIMARY.getBlue(), 150));
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(new RoundRectangle2D.Double(x, y, cardW, cardH, 24, 24));
            g2.setPaint(new GradientPaint(0, (float) y, new Color(255, 255, 255, 26), 0, (float) (y + cardH * .5), new Color(255, 255, 255, 0)));
            g2.fill(new RoundRectangle2D.Double(x + 1, y + 1, cardW - 2, cardH * .5, 23, 23));

            int insignia = 96;
            Rank.paintInsignia(g2, (int) (w / 2 - insignia / 2), (int) (y + 30), insignia, points);
            Rank.Tier tier = Rank.of(points);
            g2.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
            FontMetrics small = g2.getFontMetrics();
            g2.setColor(Theme.SEA_HORIZON);
            String kicker = "ĐANG VÀO SẢNH CHỜ";
            g2.drawString(kicker, (w - small.stringWidth(kicker)) / 2, (int) (y + 160));
            g2.setFont(Theme.FONT_TITLE.deriveFont(Font.BOLD, 26f));
            FontMetrics title = g2.getFontMetrics();
            g2.setColor(Theme.TEXT_MAIN);
            g2.drawString(commander, (w - title.stringWidth(commander)) / 2, (int) (y + 196));
            g2.setFont(Theme.FONT_BOLD);
            FontMetrics bold = g2.getFontMetrics();
            g2.setColor(tier.color());
            String rank = tier.name().toUpperCase(java.util.Locale.ROOT) + "  •  " + points + " ĐIỂM";
            g2.drawString(rank, (w - bold.stringWidth(rank)) / 2, (int) (y + 224));

            double barW = cardW - 90, barX = x + 45, barY = y + cardH - 58;
            g2.setColor(new Color(255, 255, 255, 26));
            g2.fill(new RoundRectangle2D.Double(barX, barY, barW, 10, 10, 10));
            g2.setColor(Theme.PRIMARY);
            g2.fill(new RoundRectangle2D.Double(barX, barY, Math.max(10, barW * OceanScene.clamp01(p)), 10, 10, 10));
            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.TEXT_MUTED);
            FontMetrics tiny = g2.getFontMetrics();
            String note = channel + "  •  đang nạp hạm đội và danh sách chỉ huy trực tuyến";
            g2.drawString(note, (w - tiny.stringWidth(note)) / 2, (int) (barY - 12));
            String hint = "Nhấn để vào ngay";
            g2.drawString(hint, (w - tiny.stringWidth(hint)) / 2, (int) (barY + 32));
            g2.dispose();

            double ring = 1 - OceanScene.clamp01(p);
            g2 = Theme.graphics(g);
            g2.setColor(new Color(Theme.SEA_HORIZON.getRed(), Theme.SEA_HORIZON.getGreen(), Theme.SEA_HORIZON.getBlue(), (int) (40 * ring)));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(w / 2.0 - 140, y + 30 + insignia / 2.0 - 140, 280, 280));
            g2.dispose();
            g.dispose();
        }
    }
}
