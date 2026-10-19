package client.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Base class for the in-match screens. It paints the animated sea behind everything, plays the
 * "cast off" entrance effect once when the screen appears and keeps a single timer that drives the
 * whole frame, so the boards, decorations and backdrop always animate from the same clock.
 *
 * <p>The content is expected to use translucent cards ({@link Theme#seaCard(int, int)}) so the
 * water stays visible. The effect never blocks input: every control keeps working while it plays.</p>
 */
public class OceanPanel extends JPanel {
    private static final int IDLE_DELAY_MS = 50, INTRO_DELAY_MS = 40;
    /** A window that is not focused still moves, but far more cheaply: another client may be
     *  fighting for the event thread at the same time. */
    private static final int IDLE_DELAY_SLOW_MS = 200, INTRO_DELAY_SLOW_MS = 110;
    /** Upper bound the animation backs off to when the event thread is busy with real work. */
    private static final int MAX_DELAY_MS = 220;
    private static final String[] SEA_STATES = {
            "Sóng cấp 2 • Gió Đông Bắc • Tầm nhìn 12 hải lý",
            "Sóng cấp 3 • Gió Bắc • Tầm nhìn 9 hải lý",
            "Sóng cấp 1 • Gió Đông • Biển lặng như tờ",
            "Sóng cấp 4 • Gió Tây Nam • Mưa rào rải rác",
            "Sóng cấp 2 • Gió Nam • Trời quang, mây tán"
    };
    private final Timer seaTimer;
    private final String introTitle;
    private volatile String introSubtitle;
    private final JLabel seaState = Theme.label(SEA_STATES[0], Theme.FONT_SMALL, Theme.SEA_HORIZON);
    private boolean animate = OceanScene.isAnimated();
    private long introStartedNanos, paintStartedNanos;
    private double paintedMillis;
    private int frameDelay = IDLE_DELAY_MS;
    private boolean introFinished;
    private int stateIndex = -1;

    public OceanPanel(LayoutManager layout, String introTitle, String introSubtitle) {
        super(layout);
        this.introTitle = introTitle; this.introSubtitle = introSubtitle;
        setOpaque(false);
        seaTimer = new Timer(IDLE_DELAY_MS, e -> tick());
        seaTimer.setCoalesce(true);
    }
    /** The rotating sea-state readout, meant for a header or footer. */
    public JLabel seaState() { return seaState; }
    public void setIntroSubtitle(String text) { introSubtitle = text; }
    public boolean isIntroPlaying() { return !introFinished && introStartedNanos != 0; }
    /** Ends the entrance effect immediately (used when a screen is rebuilt mid-transition). */
    public void finishIntro() { introFinished = true; introStartedNanos = 0; if (seaTimer.isRunning()) seaTimer.setDelay(IDLE_DELAY_MS); repaint(); }
    public boolean isSeaAnimated() { return animate; }
    /** Waves on/off. The sea itself stays, only the motion stops. */
    public void setSeaAnimated(boolean value) {
        animate = value; OceanScene.setAnimated(value);
        if (value) { if (isDisplayable()) seaTimer.start(); } else { seaTimer.stop(); repaint(); }
    }
    public JCheckBox animationToggle() {
        JCheckBox toggle = new JCheckBox("Sóng biển: " + (animate ? "Bật" : "Tắt"), animate);
        toggle.setOpaque(false); toggle.setForeground(Theme.TEXT_MAIN); toggle.setFont(Theme.FONT_REGULAR);
        toggle.addActionListener(e -> { setSeaAnimated(toggle.isSelected()); toggle.setText("Sóng biển: " + (animate ? "Bật" : "Tắt")); });
        return toggle;
    }
    /** Stops the shared clock; called when the screen is torn down. */
    public void disposeOcean() { seaTimer.stop(); }
    @Override public void addNotify() { super.addNotify(); if (animate) seaTimer.start(); }
    @Override public void removeNotify() { seaTimer.stop(); super.removeNotify(); }
    private void tick() {
        if (!animate) { seaTimer.stop(); return; }
        if (!introFinished && introStartedNanos != 0
                && (System.nanoTime() - introStartedNanos) / 1e9 >= OceanScene.INTRO_SECONDS) {
            introFinished = true;
        }
        boolean focused = isWindowFocused();
        int target = !introFinished ? (focused ? INTRO_DELAY_MS : INTRO_DELAY_SLOW_MS)
                                    : (focused ? IDLE_DELAY_MS : IDLE_DELAY_SLOW_MS);
        // self-tuning: if a frame costs more than half the interval, slow down instead of
        // stealing the event thread from clicks, validation and network callbacks
        if (paintedMillis > target * 0.5) frameDelay = Math.min(MAX_DELAY_MS, (int) (frameDelay * 1.25) + 3);
        else frameDelay = Math.max(target, (int) (frameDelay * 0.86) - 2);
        if (seaTimer.getDelay() != frameDelay) seaTimer.setDelay(frameDelay);
        rotateSeaState();
        repaint();
    }
    private boolean isWindowFocused() {
        java.awt.Window active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return active != null && active == javax.swing.SwingUtilities.getWindowAncestor(this);
    }
    private void rotateSeaState() {
        int index = (int) (OceanScene.time() / 9.0 % SEA_STATES.length);
        if (index != stateIndex) { stateIndex = index; seaState.setText(SEA_STATES[index]); }
    }
    @Override protected void paintComponent(Graphics graphics) {
        if (getWidth() < 8 || getHeight() < 8) return;
        paintStartedNanos = System.nanoTime();
        if (!animate) {
            // waves switched off: the entrance effect is motion too, so it is skipped as well
            introFinished = true;
        } else if (introStartedNanos == 0 && !introFinished && isShowing()) {
            introStartedNanos = System.nanoTime();
            if (!seaTimer.isRunning()) seaTimer.start();
        }
        Graphics2D g = Theme.graphics(graphics);
        OceanScene.paintBackdrop(g, getWidth(), getHeight(), animate ? OceanScene.time() : 0);
        g.dispose();
    }
    @Override protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        if (getWidth() < 8 || getHeight() < 8) return;
        Graphics2D g = Theme.graphics(graphics);
        OceanScene.paintDrift(g, getWidth(), getHeight(), animate ? OceanScene.time() : 0);
        if (!introFinished && introStartedNanos != 0) {
            double elapsed = (System.nanoTime() - introStartedNanos) / 1e9;
            OceanScene.paintIntro(g, getWidth(), getHeight(), elapsed, introTitle, introSubtitle);
        }
        g.dispose();
        paintedMillis = (System.nanoTime() - paintStartedNanos) / 1e6;
    }
}
