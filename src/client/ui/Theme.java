package client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Shared visual language for the desktop client. No external UI dependencies. */
public final class Theme {
    private Theme() { }
    public static final Color BG_DARK = new Color(16, 21, 46);
    public static final Color PANEL_BG = new Color(27, 35, 66);
    public static final Color PANEL_LIGHT = new Color(43, 54, 89);
    public static final Color BORDER_COLOR = new Color(68, 86, 132);
    public static final Color PRIMARY = new Color(87, 231, 208);
    public static final Color PRIMARY_HOVER = new Color(133, 238, 213);
    public static final Color VIOLET = new Color(168,145,255);
    public static final Color BLUE = new Color(99,180,255);
    public static final Color SUCCESS = PRIMARY;
    public static final Color WARNING = new Color(242, 194, 111);
    public static final Color DANGER = new Color(248, 125, 121);
    public static final Color TEXT_MAIN = new Color(234, 242, 248);
    public static final Color TEXT_MUTED = new Color(144, 164, 182);
    public static final Color CELL_WATER = new Color(29, 70, 112);
    public static final Color CELL_WATER_HOVER = new Color(48, 118, 163);
    public static final Color CELL_SHIP = new Color(88, 107, 191);
    public static final Color CELL_HIT = new Color(168, 68, 80);
    public static final Color CELL_MISS = new Color(27, 53, 89);
    public static final Color CELL_SUNK = new Color(85, 47, 89);
    // Sea palette used by the in-match screens: sky, open water, foam and wet glass.
    public static final Color SEA_SKY_TOP = new Color(8, 24, 46);
    public static final Color SEA_SKY_LOW = new Color(38, 92, 128);
    public static final Color SEA_SUN = new Color(255, 214, 150);
    public static final Color SEA_HORIZON = new Color(126, 202, 226);
    public static final Color SEA_MID = new Color(17, 79, 122);
    public static final Color SEA_DEEP = new Color(7, 34, 64);
    public static final Color SEA_ABYSS = new Color(4, 18, 38);
    public static final Color SEA_FOAM = new Color(226, 246, 255);
    public static final Color SEA_GLASS = new Color(9, 28, 52);
    private static final String UI_FONT = availableFont(Font.SANS_SERIF, "Segoe UI", "Helvetica Neue", "Arial");
    private static final String MONO_FONT = availableFont(Font.MONOSPACED, "Consolas", "Menlo", "DejaVu Sans Mono");
    public static final Font FONT_TITLE = new Font(UI_FONT, Font.BOLD, 28);
    public static final Font FONT_HEADER = new Font(UI_FONT, Font.BOLD, 17);
    public static final Font FONT_REGULAR = new Font(UI_FONT, Font.PLAIN, 14);
    public static final Font FONT_BOLD = new Font(UI_FONT, Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font(UI_FONT, Font.PLAIN, 12);
    public static final Font FONT_GRID = new Font(MONO_FONT, Font.PLAIN, 12);

    private static String availableFont(String fallback, String... candidates) {
        java.util.Set<String> installed = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidate : candidates) {
            if (installed.contains(candidate) && new Font(candidate, Font.PLAIN, 14)
                    .canDisplayUpTo("Hải chiến • Đăng nhập 0123456789") == -1) return candidate;
        }
        return fallback;
    }

    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) { }
        UIManager.put("Panel.background", PANEL_BG);
        UIManager.put("OptionPane.background", PANEL_BG);
        UIManager.put("OptionPane.messageForeground", TEXT_MAIN);
        UIManager.put("OptionPane.yesButtonText", "Đồng ý");
        UIManager.put("OptionPane.noButtonText", "Không");
        UIManager.put("OptionPane.cancelButtonText", "Hủy");
        UIManager.put("OptionPane.okButtonText", "Đóng");
        UIManager.put("Label.foreground", TEXT_MAIN);
        UIManager.put("Label.font", FONT_REGULAR);
        UIManager.put("Button.font", FONT_BOLD);
        UIManager.put("Button.background", PANEL_LIGHT);
        UIManager.put("Button.foreground", TEXT_MAIN);
        UIManager.put("TextField.selectionBackground", new Color(44, 92, 99));
        UIManager.put("TextField.selectionForeground", TEXT_MAIN);
        UIManager.put("ToolTip.background", PANEL_LIGHT);
        UIManager.put("ToolTip.foreground", TEXT_MAIN);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER_COLOR));
        UIManager.put("ScrollBar.width", 9);
    }
    public static Graphics2D graphics(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }
    /** Off-screen surface with the same quality hints, for cached layers and sprites. */
    public static Graphics2D graphics(BufferedImage image) {
        return graphics(image.createGraphics());
    }
    public static void styleButton(JButton button, Color background, Color foreground) {
        if(button.getClientProperty("audio") == null){button.putClientProperty("audio",true);button.addActionListener(e->GameAudio.play(GameAudio.Sound.CLICK));}
        button.setBackground(background); button.setForeground(foreground); button.setFont(FONT_BOLD);
        button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false);
        button.setFocusPainted(false); button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(11, 17, 11, 17));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setUI(new BasicButtonUI() {
            @Override public void paint(Graphics g, JComponent c) {
                AbstractButton b = (AbstractButton) c;
                Graphics2D g2 = graphics(g);
                Color fill = b.isEnabled() ? b.getBackground() : PANEL_LIGHT;
                if (b.isEnabled() && b.getModel().isRollover()) fill = blend(fill, Color.WHITE, .09f);
                if (b.getModel().isPressed()) fill = blend(fill, BG_DARK, .15f);
                g2.setColor(fill); g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 12, 12);
                if (b.hasFocus()) {
                    g2.setColor(PRIMARY); g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(2, 2, c.getWidth()-5, c.getHeight()-5, 10, 10);
                }
                g2.dispose(); super.paint(g, c);
            }
            @Override protected void paintText(Graphics g, AbstractButton b, Rectangle rect, String text) {
                g.setColor(b.isEnabled() ? b.getForeground() : TEXT_MUTED);
                g.drawString(text, rect.x, rect.y + g.getFontMetrics().getAscent());
            }
        });
    }
    public static Color blend(Color a, Color b, float amount) {
        return new Color((int)(a.getRed()*(1-amount)+b.getRed()*amount),
                (int)(a.getGreen()*(1-amount)+b.getGreen()*amount),
                (int)(a.getBlue()*(1-amount)+b.getBlue()*amount));
    }
    public static void stylePrimaryButton(JButton b) { styleButton(b, PRIMARY, BG_DARK); }
    public static void styleSuccessButton(JButton b) { stylePrimaryButton(b); }
    public static void styleDangerButton(JButton b) { styleButton(b, DANGER, BG_DARK); }
    public static JButton button(String text, boolean primary) {
        JButton b = new JButton(text);
        if (primary) stylePrimaryButton(b); else styleButton(b, PANEL_LIGHT, TEXT_MAIN);
        return b;
    }
    public static JPanel transparent(LayoutManager layout) {
        JPanel p = new JPanel(layout); p.setOpaque(false); return p;
    }
    public static JPanel stack() { JPanel p = transparent(null); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text); l.setFont(font); l.setForeground(color); return l;
    }
    public static JLabel muted(String text) { return label(text, FONT_SMALL, TEXT_MUTED); }
    public static void addStack(JPanel p, JComponent c, int gap) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT); p.add(c);
        if (gap > 0) p.add(Box.createVerticalStrut(gap));
    }
    public static JPanel createCardPanel() { return card(PANEL_BG, 20); }
    public static JPanel card(Color color, int padding) {
        JPanel p = new RoundedPanel(color);
        p.setBorder(new EmptyBorder(padding, padding, padding, padding)); return p;
    }
    /** A card that stays see-through so the animated sea behind it remains readable. */
    public static JPanel glassCard(int padding) { return glassCard(SEA_GLASS, 156, padding); }
    public static JPanel glassCard(Color base, int alpha, int padding) {
        JPanel p = new RoundedPanel(base, alpha);
        p.setBorder(new EmptyBorder(padding, padding, padding, padding)); return p;
    }
    /** In-match card: wet glass sheen, horizon-tinted outline, optional wave line under the title. */
    public static JPanel seaCard(int alpha, int padding) {
        RoundedPanel p = new RoundedPanel(SEA_GLASS, alpha); p.setSheen(true);
        p.setBorder(new EmptyBorder(padding, padding, padding, padding)); return p;
    }
    public static class RoundedPanel extends JPanel {
        private int fillAlpha = 255;
        private boolean sheen, outlined = true;
        public RoundedPanel(Color color) { this(color, 255); }
        public RoundedPanel(Color color, int fillAlpha) { setBackground(color); setOpaque(false); this.fillAlpha = fillAlpha; }
        public int getFillAlpha() { return fillAlpha; }
        public void setFillAlpha(int value) { fillAlpha = value; repaint(); }
        public void setSheen(boolean value) { sheen = value; }
        public void setOutlined(boolean value) { outlined = value; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = graphics(g);
            int w = getWidth()-1, h = getHeight()-1; Color fill = getBackground();
            if (fillAlpha < 255) {
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.max(0, fillAlpha)));
                g2.fillRoundRect(0, 0, w, h, 20, 20);
            } else { g2.setColor(fill); g2.fillRoundRect(0, 0, w, h, 20, 20); }
            if (sheen) {
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 28), 0, Math.max(1, h / 2), new Color(255, 255, 255, 0)));
                g2.fillRoundRect(1, 1, w-1, h-1, 19, 19);
            }
            if (outlined) {
                Color line = fillAlpha < 255 ? new Color(SEA_HORIZON.getRed(), SEA_HORIZON.getGreen(), SEA_HORIZON.getBlue(), 78) : BORDER_COLOR;
                g2.setColor(line); g2.drawRoundRect(0, 0, w, h, 20, 20);
            }
            g2.dispose(); super.paintComponent(g);
        }
    }
    /** Title text with a foam-to-cyan gradient and an optional light sweep, for the sea screens. */
    public static class SeaTitle extends JLabel {
        private boolean shimmer;
        public SeaTitle(String text, Font font) { super(text); setFont(font); setForeground(SEA_FOAM); }
        public void setShimmer(boolean value) { shimmer = value; }
        @Override public void paintComponent(Graphics g) {
            Graphics2D g2 = graphics(g);
            FontMetrics metrics = g2.getFontMetrics(getFont());
            String text = getText(); if (text == null || text.isEmpty()) { g2.dispose(); return; }
            int x = getInsets().left, y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            if (shimmer) {
                double sweep = (System.nanoTime() / 1e9 % 6.0) / 6.0;
                int span = Math.max(1, metrics.stringWidth(text));
                g2.setPaint(new GradientPaint(x, 0, SEA_FOAM, x + span, 0, PRIMARY));
                int edge = x + (int) (sweep * span * 1.6) - span / 3;
                Shape clip = g2.getClip();
                g2.setColor(new Color(255, 255, 255, 120));
                if (edge + 90 > x && edge < x + span) {
                    g2.clipRect(edge, 0, 90, getHeight());
                    g2.setFont(getFont()); g2.drawString(text, x, y);
                }
                g2.setClip(clip);
            }
            g2.setColor(SEA_ABYSS); g2.setFont(getFont()); g2.drawString(text, x + 1, y + 1);
            g2.setPaint(new GradientPaint(x, y - metrics.getAscent(), SEA_FOAM, x, y + metrics.getDescent(), new Color(120, 226, 226)));
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }
    public static void styleField(JTextField field) {
        field.setFont(FONT_REGULAR); field.setBackground(BG_DARK); field.setForeground(TEXT_MAIN);
        field.setCaretColor(PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COLOR), new EmptyBorder(10, 12, 10, 12)));
        field.setPreferredSize(new Dimension(160, 42));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) { border(PRIMARY); }
            public void focusLost(java.awt.event.FocusEvent e) { border(BORDER_COLOR); }
            private void border(Color c) { field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c), new EmptyBorder(10, 12, 10, 12))); }
        });
    }
    public static JPanel field(String title, JTextField input) {
        styleField(input); JPanel p = transparent(new BorderLayout(0, 7));
        JLabel label = label(title, FONT_SMALL, TEXT_MUTED); label.setLabelFor(input);
        p.add(label, BorderLayout.NORTH); p.add(input, BorderLayout.CENTER); return p;
    }
    public static JLabel badge(String text, Color color) {
        JLabel l = label(text, FONT_SMALL.deriveFont(Font.BOLD), color);
        l.setBorder(new EmptyBorder(7, 11, 7, 11)); l.setOpaque(true);
        l.setBackground(blend(PANEL_BG, color, .10f)); return l;
    }
    public static JPanel heading(String eyebrow, String title, String subtitle) {
        JPanel p = stack();
        addStack(p, label(eyebrow, FONT_SMALL.deriveFont(Font.BOLD), PRIMARY), 8);
        addStack(p, label(title, FONT_TITLE, TEXT_MAIN), 6);
        addStack(p, label(subtitle, FONT_REGULAR, TEXT_MUTED), 0); return p;
    }
    public static JPanel steps(int active) {
        JPanel p = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        String[] labels = {"01  Vào sảnh", "02  Bố trí hạm đội", "03  Chiến đấu"};
        for (int i=0;i<labels.length;i++) {
            if(i>0) p.add(label("    /    ", FONT_SMALL, BORDER_COLOR));
            p.add(label(labels[i], FONT_SMALL, i==active ? PRIMARY : TEXT_MUTED));
        }
        return p;
    }
    public static void styleTable(JTable table) {
        table.setFont(FONT_REGULAR); table.setForeground(TEXT_MAIN); table.setBackground(PANEL_BG);
        table.setRowHeight(48); table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(PANEL_LIGHT); table.setSelectionForeground(PRIMARY);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t,Object v,boolean s,boolean f,int r,int c) {
                super.getTableCellRendererComponent(t,v,s,f,r,c);
                setFont(FONT_SMALL);setForeground(TEXT_MUTED);setBackground(PANEL_BG);
                setBorder(new EmptyBorder(0,12,0,12)); return this;
            }
        });
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t,Object v,boolean s,boolean f,int r,int c) {
                super.getTableCellRendererComponent(t,v,s,f,r,c);
                setForeground(s?PRIMARY:TEXT_MAIN); setBackground(s?PANEL_LIGHT:(r%2==0?PANEL_BG:blend(PANEL_BG,PANEL_LIGHT,.35f)));
                setBorder(new EmptyBorder(0,12,0,12)); return this;
            }
        });
    }
    /** Lets the sea behind a scrollable list show through instead of painting a panel colour. */
    public static void transparentScroll(JScrollPane scroll) {
        Color clear = new Color(0, 0, 0, 0);
        scroll.setOpaque(false); scroll.setBackground(clear);
        scroll.getViewport().setOpaque(false); scroll.getViewport().setBackground(clear);
    }
    public static JScrollPane scroll(Component component) {
        JScrollPane s = new JScrollPane(component); s.setBorder(BorderFactory.createEmptyBorder());
        s.setOpaque(false); s.getViewport().setBackground(PANEL_BG); s.getVerticalScrollBar().setUnitIncrement(20);
        s.getVerticalScrollBar().setUI(new BasicScrollBarUI(){
            protected void configureScrollBarColors(){thumbColor=BORDER_COLOR;trackColor=PANEL_BG;}
            protected JButton createDecreaseButton(int orientation){return zeroButton();}
            protected JButton createIncreaseButton(int orientation){return zeroButton();}
            private JButton zeroButton(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));return b;}
            protected void paintThumb(Graphics g,JComponent c,Rectangle r){Graphics2D g2=graphics(g);g2.setColor(isThumbRollover()?TEXT_MUTED:BORDER_COLOR);g2.fillRoundRect(r.x+2,r.y,r.width-4,r.height,6,6);g2.dispose();}
        });return s;
    }
    /** A content area that scrolls only when its minimum useful layout no longer fits. */
    public static JScrollPane scrollPage(JComponent content,int height,Color background) {
        class Page extends JPanel implements Scrollable {
            Page(){super(new BorderLayout());setOpaque(false);add(content);setPreferredSize(new Dimension(1,height));}
            public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
            public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 22;}
            public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(22,r.height-40);}
            public boolean getScrollableTracksViewportWidth(){return true;}
            public boolean getScrollableTracksViewportHeight(){return getParent()!=null&&getParent().getHeight()>=height;}
        }
        JScrollPane s=scroll(new Page());s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);s.getViewport().setBackground(background);return s;
    }
    public static void configureWindow(JFrame frame, int width, int height, int minWidth, int minHeight) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(width,screen.width),Math.min(height,screen.height));
        frame.setMinimumSize(new Dimension(Math.min(minWidth,screen.width),Math.min(minHeight,screen.height)));
        frame.setLocationRelativeTo(null); frame.getContentPane().setBackground(BG_DARK);
        BufferedImage icon = new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        Graphics2D base=icon.createGraphics();Graphics2D g = graphics(base);
        g.setColor(PANEL_BG);g.fillRoundRect(0,0,32,32,8,8);
        g.setColor(PRIMARY);g.setStroke(new BasicStroke(2));g.drawOval(5,5,21,21);g.drawLine(16,2,16,30);g.drawLine(2,16,30,16);g.dispose();base.dispose();
        frame.setIconImage(icon);
    }
    public static String html(String text) {
        return text==null?"":text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
}
