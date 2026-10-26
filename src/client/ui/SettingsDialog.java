package client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import java.util.prefs.Preferences;

/**
 * Volume and comfort settings: master, effects and sea-ambience levels plus the sound, wave and
 * minimap switches. Every change applies immediately (and to the screens already open) and is
 * stored in the user preferences, so the next launch starts the same way.
 */
public class SettingsDialog extends JDialog {
    private static final Preferences BATTLE_PREF = Preferences.userNodeForPackage(BattlePanel.class);

    public static void open(Component parent) {
        for (Window window : Window.getWindows())
            if (window instanceof SettingsDialog && window.isShowing()) {
                window.toFront();
                window.requestFocus();
                return;
            }
        SettingsDialog dialog = new SettingsDialog(parent);
        dialog.setVisible(true);
    }

    private SettingsDialog(Component parent) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), "Battleship • Cài đặt", ModalityType.MODELESS);
        JPanel body = Theme.stack();
        body.setOpaque(true);
        body.setBackground(Theme.BG_DARK);
        body.setBorder(new EmptyBorder(22, 24, 20, 24));

        Theme.addStack(body, Theme.badge("CÀI ĐẶT", Theme.PRIMARY), 10);
        Theme.addStack(body, Theme.label("Âm thanh và hiển thị", Theme.FONT_TITLE.deriveFont(24f), Theme.TEXT_MAIN), 6);
        Theme.addStack(body, Theme.muted("Mọi thay đổi được áp dụng ngay và ghi nhớ cho lần chơi sau."), 16);

        JCheckBox sound = toggle("Bật âm thanh", GameAudio.isEnabled());
        sound.addActionListener(e -> GameAudio.setEnabled(sound.isSelected()));
        Theme.addStack(body, sound, 14);

        JSlider master = slider(GameAudio.getMasterVolume());
        Theme.addStack(body, sliderRow("Âm lượng tổng", master), 14);
        JSlider effects = slider(GameAudio.getSfxVolume());
        Theme.addStack(body, sliderRow("Hiệu ứng", effects), 14);
        JSlider sea = slider(GameAudio.getAmbienceVolume());
        Theme.addStack(body, sliderRow("Tiếng biển", sea), 14);
        master.addChangeListener(e -> { GameAudio.setMasterVolume(master.getValue() / 100f); preview(master); });
        effects.addChangeListener(e -> { GameAudio.setSfxVolume(effects.getValue() / 100f); preview(effects); });
        sea.addChangeListener(e -> { GameAudio.setAmbienceVolume(sea.getValue() / 100f); preview(sea); });

        JCheckBox waves = toggle("Sóng biển động", OceanScene.isAnimated());
        waves.addActionListener(e -> { OceanScene.setAnimated(waves.isSelected()); walk(waves.isSelected(), BATTLE_PREF.getBoolean("minimap", true)); });
        Theme.addStack(body, waves, 14);

        JCheckBox minimap = toggle("Bản đồ thu nhỏ trong trận", BATTLE_PREF.getBoolean("minimap", true));
        minimap.addActionListener(e -> { BATTLE_PREF.putBoolean("minimap", minimap.isSelected()); walk(OceanScene.isAnimated(), minimap.isSelected()); });
        Theme.addStack(body, minimap, 14);

        JPanel actions = Theme.transparent(new GridLayout(1, 3, 10, 0));
        JButton test = Theme.button("Nghe thử", false);
        test.addActionListener(e -> { GameAudio.play(GameAudio.Sound.PING); GameAudio.play(GameAudio.Sound.SUNK); });
        JButton reset = Theme.button("Mặc định", false);
        reset.addActionListener(e -> {
            GameAudio.resetLevels();
            master.setValue(Math.round(GameAudio.getMasterVolume() * 100));
            effects.setValue(Math.round(GameAudio.getSfxVolume() * 100));
            sea.setValue(Math.round(GameAudio.getAmbienceVolume() * 100));
            sound.setSelected(GameAudio.isEnabled());
            waves.setSelected(OceanScene.isAnimated());
            BATTLE_PREF.putBoolean("minimap", true);
            minimap.setSelected(true);
            walk(OceanScene.isAnimated(), true);
        });
        JButton close = Theme.button("Đóng", true);
        close.addActionListener(e -> dispose());
        actions.add(test); actions.add(reset); actions.add(close);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        Theme.addStack(body, actions, 0);

        setContentPane(body);        setSize(560, 560);
        setResizable(false);
        setLocationRelativeTo(getOwner());
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Volume sliders play a soft cue while dragging, so the level is audible right away. */
    private long lastPreview;
    private void preview(JSlider slider) {
        if (!slider.getValueIsAdjusting()) return;
        long now = System.nanoTime();
        if (now - lastPreview < 220_000_000L) return;
        lastPreview = now;
        GameAudio.play(GameAudio.Sound.SELECT);
    }

    private static JCheckBox toggle(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setOpaque(false); box.setForeground(Theme.TEXT_MAIN);
        box.setFont(Theme.FONT_REGULAR); box.setFocusPainted(false);
        return box;
    }
    private static JSlider slider(float value) {
        JSlider slider = new JSlider(0, 100, Math.round(value * 100));
        slider.setOpaque(false); slider.setForeground(Theme.PRIMARY);
        slider.setMajorTickSpacing(25); slider.setPaintTicks(true);
        return slider;
    }
    private JPanel sliderRow(String title, JSlider slider) {
        JPanel row = Theme.stack();
        JLabel value = Theme.label(Math.round(slider.getValue()) + "%", Theme.FONT_BOLD, Theme.PRIMARY);
        JPanel head = Theme.transparent(new BorderLayout());
        head.add(Theme.label(title, Theme.FONT_REGULAR, Theme.TEXT_MAIN), BorderLayout.WEST);
        head.add(value, BorderLayout.EAST);
        slider.addChangeListener(e -> value.setText(Math.round(slider.getValue()) + "%"));
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        Theme.addStack(row, head, 2);
        Theme.addStack(row, slider, 0);
        return row;
    }

    /** One pass over the open windows keeps the sea and minimap switches in sync everywhere. */
    private static void walk(boolean animated, boolean minimap) {
        for (Window window : Window.getWindows()) apply(window, animated, minimap);
    }
    private static void apply(Component component, boolean animated, boolean minimap) {
        if (component instanceof OceanPanel) ((OceanPanel) component).setSeaAnimated(animated);
        if (component instanceof BattlePanel) ((BattlePanel) component).setMinimapVisible(minimap);
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) apply(child, animated, minimap);
    }
}
