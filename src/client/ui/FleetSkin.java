package client.ui;

import java.awt.*;
import java.util.prefs.Preferences;

public final class FleetSkin {
    private FleetSkin() { }

    public static final class Scheme {
        private final String name;
        private final Color hull, deck, trim, hullDark;
        Scheme(String name, Color hull, Color deck, Color trim) {
            this.name = name;
            this.hull = hull;
            this.deck = deck;
            this.trim = trim;
            this.hullDark = Theme.blend(hull, Theme.BG_DARK, .45f);
        }
        public String name() { return name; }
        public Color hull() { return hull; }
        public Color deck() { return deck; }
        public Color trim() { return trim; }
        public Color hullDark() { return hullDark; }
    }

    private static final Scheme[] SCHEMES = {
            new Scheme("Hạm đội Bạc", new Color(226, 240, 248), new Color(126, 158, 180), new Color(255, 255, 255)),
            new Scheme("Đồng Xanh", new Color(150, 214, 196), new Color(52, 132, 118), new Color(212, 246, 236)),
            new Scheme("Hổ Phách", new Color(248, 214, 140), new Color(186, 126, 44), new Color(255, 240, 200)),
            new Scheme("Huyết Long", new Color(240, 150, 138), new Color(160, 54, 48), new Color(255, 216, 208)),
            new Scheme("Bóng Đêm", new Color(120, 140, 168), new Color(48, 66, 92), new Color(170, 196, 224)),
            new Scheme("San Hô", new Color(250, 190, 168), new Color(198, 98, 78), new Color(255, 232, 220))
    };

    private static final Preferences PREF = Preferences.userNodeForPackage(FleetSkin.class);
    private static final String KEY_INDEX = "fleet_scheme";
    private static final String KEY_NAME = "fleet_name";

    private static volatile int index = Math.max(0, Math.min(SCHEMES.length - 1, PREF.getInt(KEY_INDEX, 0)));
    private static volatile String fleetName = PREF.get(KEY_NAME, "Hạm đội của tôi");
    private static volatile int version = 1;

    public static int version() { return version; }

    public static Scheme[] schemes() { return SCHEMES.clone(); }

    public static Scheme current() { return SCHEMES[Math.max(0, Math.min(SCHEMES.length - 1, index))]; }

    public static int index() { return index; }

    public static String fleetName() { return fleetName; }

    public static void apply(int schemeIndex, String name) {
        index = Math.max(0, Math.min(SCHEMES.length - 1, schemeIndex));
        PREF.putInt(KEY_INDEX, index);
        if (name != null && !name.trim().isEmpty()) {
            fleetName = name.trim().length() > 24 ? name.trim().substring(0, 24) : name.trim();
            PREF.put(KEY_NAME, fleetName);
        }
        version++;
    }

    public static void reset() {
        index = 0;
        fleetName = "Hạm đội của tôi";
        PREF.putInt(KEY_INDEX, 0);
        PREF.put(KEY_NAME, fleetName);
        version++;
    }

    public static void paintFleetPreview(Graphics2D g, int x, int y, int width, int height) {
        Graphics2D g2 = Theme.graphics(g);
        Scheme scheme = current();
        g2.setColor(new Color(6, 28, 40, 200));
        g2.fillRoundRect(x, y, width, height, 14, 14);
        g2.setColor(new Color(Theme.SEA_HORIZON.getRed(), Theme.SEA_HORIZON.getGreen(), Theme.SEA_HORIZON.getBlue(), 90));
        g2.drawRoundRect(x, y, width - 1, height - 1, 14, 14);
        int[] sizes = common.config.GameConfig.SHIP_SIZES;
        int rows = sizes.length;
        int rowHeight = Math.max(8, (height - 20) / rows);
        int maxSize = sizes[0];
        int span = Math.max(60, width - 44);
        for (int i = 0; i < rows; i++) {
            int cellWidth = Math.max(6, span / maxSize);
            int length = cellWidth * sizes[i];
            int rowY = y + 12 + i * rowHeight + rowHeight / 2 - 4;
            int bowX = x + 22;
            int[] hullX = {bowX, bowX + Math.max(4, cellWidth / 2), bowX + length, bowX + length, bowX + Math.max(4, cellWidth / 2)};
            int[] hullY = {rowY + 4, rowY - 4, rowY - 4, rowY + 4, rowY + 4};
            g2.setColor(new Color(3, 16, 24, 130));
            g2.fillPolygon(new int[]{hullX[0], hullX[1], hullX[2], hullX[3], hullX[4]},
                    new int[]{hullY[0] + 1, hullY[1] + 1, hullY[2] + 1, hullY[3] + 1, hullY[4] + 1}, 5);
            g2.setColor(scheme.hull());
            g2.fillPolygon(hullX, hullY, 5);
            g2.setColor(scheme.hullDark());
            g2.setStroke(new BasicStroke(1f));
            g2.drawPolygon(hullX, hullY, 5);
            g2.setColor(scheme.deck());
            g2.fillRect(bowX + Math.max(6, cellWidth), rowY - 2, Math.max(2, length - Math.max(8, cellWidth * 2)), 4);
            g2.setColor(scheme.trim());
            g2.fillRect(bowX + Math.max(8, cellWidth * 2), rowY - 1, 3, 2);
        }
        g2.setFont(Theme.FONT_SMALL);
        g2.setColor(Theme.TEXT_MUTED);
        String caption = sizes.length + " tàu • " + scheme.name();
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(caption, x + width - metrics.stringWidth(caption) - 12, y + height - 10);
        g2.dispose();
    }
}
