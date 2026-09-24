package client.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class Theme {
    // Colors
    public static final Color BG_DARK = new Color(15, 23, 42);          // Slate 900
    public static final Color PANEL_BG = new Color(30, 41, 59);         // Slate 800
    public static final Color PANEL_LIGHT = new Color(51, 65, 85);      // Slate 700
    public static final Color BORDER_COLOR = new Color(71, 85, 105);    // Slate 600

    public static final Color PRIMARY = new Color(2, 132, 199);        // Sky 600
    public static final Color PRIMARY_HOVER = new Color(3, 105, 161);  // Sky 700
    public static final Color SUCCESS = new Color(16, 185, 129);       // Emerald 500
    public static final Color WARNING = new Color(245, 158, 11);       // Amber 500
    public static final Color DANGER = new Color(239, 68, 68);         // Red 500
    
    // Grid Cells
    public static final Color CELL_WATER = new Color(23, 37, 84);       // Deep Ocean
    public static final Color CELL_WATER_HOVER = new Color(30, 58, 138);
    public static final Color CELL_SHIP = new Color(71, 85, 105);       // Steel gray
    public static final Color CELL_HIT = new Color(220, 38, 38);        // Fire Red
    public static final Color CELL_MISS = new Color(100, 116, 139);     // Splash Gray
    public static final Color CELL_SUNK = new Color(127, 29, 29);       // Dark Crimson

    // Text
    public static final Color TEXT_MAIN = new Color(248, 250, 252);
    public static final Color TEXT_MUTED = new Color(148, 163, 184);

    // Fonts
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_GRID = new Font("Segoe UI", Font.BOLD, 11);

    public static void styleButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    public static void stylePrimaryButton(JButton btn) {
        styleButton(btn, PRIMARY, TEXT_MAIN);
    }

    public static void styleSuccessButton(JButton btn) {
        styleButton(btn, SUCCESS, TEXT_MAIN);
    }

    public static void styleDangerButton(JButton btn) {
        styleButton(btn, DANGER, TEXT_MAIN);
    }

    public static JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(16, 16, 16, 16)
        ));
        return p;
    }
}
