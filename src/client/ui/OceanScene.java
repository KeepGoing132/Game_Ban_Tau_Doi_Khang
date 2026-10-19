package client.ui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.prefs.Preferences;

/**
 * The open sea, painted with Java2D primitives only: sky, swell, foam, god rays, gulls and the
 * "cast off" sequence that plays when a match screen appears. Every frame is a pure function of a
 * time value, so the scene can be sampled at any point without keeping per-particle state.
 *
 * <p>Large slow layers (sky, water body, vignette, foam crests) are cached as images or texture
 * sprites; a full frame costs a handful of blits plus a few dozen vector shapes, which keeps the
 * animated background affordable next to the 10x10 board repaint.</p>
 */
public final class OceanScene {
    private OceanScene() { }
    private static final Preferences PREF = Preferences.userNodeForPackage(OceanScene.class);
    private static volatile boolean animated = PREF.getBoolean("ocean", true);
    /** Seconds the "cast off" entrance effect runs for. */
    public static final double INTRO_SECONDS = 2.45;
    public static boolean isAnimated() { return animated; }
    public static void setAnimated(boolean value) { animated = value; PREF.putBoolean("ocean", value); }
    /** Monotonic clock shared by every sea painter. */
    public static double time() { return System.nanoTime() / 1e9; }

    // ---------------------------------------------------------------- math helpers
    /** Deterministic pseudo random in [0,1): lets a frame be rebuilt from a single time value. */
    public static double noise(int seed) {
        int x = seed * 374761393 + 668265263; x = (x ^ (x >>> 13)) * 1274126177;
        return ((x ^ (x >>> 16)) & 0x7fffffff) / 2147483648.0;
    }
    public static double clamp01(double value) { return value < 0 ? 0 : value > 1 ? 1 : value; }
    public static double easeOutCubic(double p) { double q = 1 - clamp01(p); return 1 - q * q * q; }
    public static double easeInOutSine(double p) { return -(Math.cos(Math.PI * clamp01(p)) - 1) / 2; }
    public static double easeOutBack(double p) { double q = clamp01(p) - 1; return 1 + q * q * (2.4 * q + 1.4); }
    public static double lerp(double a, double b, double amount) { return a + (b - a) * amount; }
    private static Color alpha(Color color, double amount) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.max(0, Math.min(255, amount)));
    }

    // ---------------------------------------------------------------- cached layers
    private static int cachedWidth, cachedHeight;
    private static long cacheBuiltNanos;
    /** Sky, water body and vignette are static: they are baked into one opaque layer. */
    private static BufferedImage backdrop;
    private static BufferedImage[][] crests;
    private static BufferedImage caustics, ray;
    /** Crest sprites are used unscaled so Java2D can tile them with a plain blit. */
    private static final int[] CREST_WIDTH = {128, 160, 208, 264, 336};
    private static final int[] CREST_HEIGHT = {30, 38, 48, 60, 76};
    /** A resize storm must not rebuild the baked layers on every intermediate size. */
    private static final long REBUILD_SETTLE_MS = 140;

    private static void ensureLayers(int width, int height) {
        if (backdrop != null && cachedWidth == width && cachedHeight == height) return;
        long now = System.nanoTime();
        if (backdrop != null && (now - cacheBuiltNanos) / 1000000L < REBUILD_SETTLE_MS) return;
        cachedWidth = width; cachedHeight = height; cacheBuiltNanos = now;
        int horizon = horizon(height);
        backdrop = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D s = Theme.graphics(backdrop);
        s.setPaint(new GradientPaint(0, 0, Theme.SEA_SKY_TOP, 0, horizon, Theme.SEA_SKY_LOW));
        s.fillRect(0, 0, width, horizon + 1);
        s.setColor(Theme.SEA_HORIZON); s.fillRect(0, horizon, width, 1);
        s.setColor(alpha(Theme.SEA_HORIZON, 40)); s.fillRect(0, horizon + 1, width, 2);
        paintSun(s, width, height, horizon);
        paintIslands(s, width, horizon);
        s.setPaint(new GradientPaint(0, horizon, alpha(Theme.SEA_MID, 235), 0, horizon + Math.max(1, (height - horizon) / 2), Theme.SEA_DEEP));
        s.fillRect(0, horizon, width, height - horizon);
        s.setPaint(new GradientPaint(0, horizon + Math.max(1, (height - horizon) / 2), alpha(Theme.SEA_DEEP, 120), 0, height, Theme.SEA_ABYSS));
        s.fillRect(0, horizon, width, height - horizon);
        for (int i = 0; i < 14; i++) {
            double x = noise(900 + i) * width, depth = 0.25 + noise(950 + i) * 0.7, length = (height - horizon) * (0.25 + noise(990 + i) * 0.5);
            s.setColor(new Color(120, 200, 226, (int) (7 + 8 * (1 - depth))));
            s.fillRect((int) x, horizon + (int) (depth * (height - horizon)), Math.max(2, (int) (width / 180.0)), (int) length);
        }
        float[] fractions = {0.45f, 1f};
        s.setPaint(new RadialGradientPaint(new Point2D.Double(width * 0.5, height * 0.44),
                (float) Math.max(width, height) * 0.78f, fractions,
                new Color[] {new Color(0, 0, 0, 0), new Color(2, 12, 26, 168)}));
        s.fillRect(0, 0, width, height);
        s.dispose();
    }
    public static int horizon(int height) { return Math.max(90, (int) (height * 0.30)); }
    private static void paintSun(Graphics2D g, int width, int height, int horizon) {
        double cx = width * 0.78, cy = horizon - height * 0.10;
        float radius = (float) Math.max(width, height) * 0.34f;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), radius, new float[] {0f, 0.18f, 1f},
                new Color[] { alpha(Theme.SEA_SUN, 92), alpha(Theme.SEA_SUN, 34), alpha(Theme.SEA_SUN, 0) }));
        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
        double disc = Math.max(14, height * 0.035);
        g.setColor(alpha(Theme.SEA_SUN, 150)); g.fill(new Ellipse2D.Double(cx - disc, cy - disc, disc * 2, disc * 2));
        g.setColor(alpha(Color.WHITE, 110)); g.fill(new Ellipse2D.Double(cx - disc * 0.5, cy - disc * 0.5, disc, disc));
    }
    private static void paintIslands(Graphics2D g, int width, int horizon) {
        g.setColor(new Color(9, 42, 62, 190));
        for (int island = 0; island < 2; island++) {
            double baseX = width * (island == 0 ? 0.16 : 0.42), spread = width * (island == 0 ? 0.10 : 0.055), peak = width * 0.018;
            Path2D hill = new Path2D.Double(); hill.moveTo(baseX - spread, horizon);
            hill.curveTo(baseX - spread * 0.5, horizon - peak, baseX - spread * 0.1, horizon - peak * 1.15, baseX, horizon - peak * 0.5);
            hill.curveTo(baseX + spread * 0.25, horizon - peak * 1.4, baseX + spread * 0.7, horizon - peak * 0.3, baseX + spread, horizon);
            hill.closePath(); g.fill(hill);
        }
        // far-off sails on the horizon line
        g.setColor(new Color(196, 226, 236, 150));
        for (int i = 0; i < 3; i++) {
            double x = width * (0.30 + i * 0.07), size = 4 + i * 1.6;
            g.fillPolygon(new int[] {(int) x, (int) (x + size), (int) x},
                    new int[] {horizon - (int) size * 3, horizon - (int) size * 2, horizon - 1}, 3);
        }
    }
    /** Foam crest sprites, far (bright, tight) to near (dark, stretched), in two looks each. */
    private static BufferedImage[][] crests() {
        if (crests != null) return crests;
        BufferedImage[][] sprites = new BufferedImage[CREST_WIDTH.length][2];
        for (int variant = 0; variant < CREST_WIDTH.length; variant++)
            for (int look = 0; look < 2; look++) {
                int width = CREST_WIDTH[variant], height = CREST_HEIGHT[variant];
                BufferedImage sprite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = Theme.graphics(sprite);
                double calm = variant / (double) (CREST_WIDTH.length - 1);
                Path2D crest = new Path2D.Double();
                for (int i = 0; i <= 48; i++) {
                    double x = i * width / 48.0, y = crestY(x, width, calm, height, look);
                    if (i == 0) crest.moveTo(x, y); else crest.lineTo(x, y);
                }
                Path2D body = (Path2D) crest.clone(); body.lineTo(width, height); body.lineTo(0, height); body.closePath();
                g.setPaint(new GradientPaint(0, 0, new Color(154, 224, 244, (int) (84 - calm * 32)), 0, height, new Color(154, 224, 244, 0)));
                g.fill(body);
                g.setColor(new Color(232, 250, 255, (int) (118 - calm * 50)));
                g.setStroke(new BasicStroke(1.6f)); g.draw(crest);
                for (int i = 0; i < 18; i++) {
                    double x = i * width / 18.0, y = crestY(x, width, calm, height, look), length = 5 + noise(variant * 31 + i + look * 7) * width * 0.09;
                    g.setColor(new Color(240, 253, 255, (int) (26 + noise(i * 7 + variant + look * 3) * 70)));
                    g.setStroke(new BasicStroke((float) (0.9 + noise(i + variant * 5) * 1.4)));
                    g.draw(new Line2D.Double(x, y + 1.5, x + length, y + 1.5 + noise(i * 3 + look) * 2));
                }
                g.dispose(); sprites[variant][look] = sprite;
            }
        return crests = sprites;
    }
    private static double crestY(double x, double width, double calm, double height, int look) {
        double k = 2 * Math.PI / width, span = height - calm * 10;
        double shape = (7 - calm * 3.4) * Math.sin((look + 2) * k * x) + (4 - calm * 2.2) * Math.sin(5 * k * x + 1.3 + look)
                + 2.4 * Math.sin(9 * k * x + 0.6);
        return span * 0.34 + shape * (0.55 + calm * 0.45);
    }
    private static void band(Graphics2D g, int index, double top, double width, double scroll, double alpha, int look) {
        BufferedImage sprite = crests()[index][look];
        int spriteWidth = sprite.getWidth(), spriteHeight = sprite.getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        double offsetX = -(int) scroll % spriteWidth;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
        g2.setPaint(new TexturePaint(sprite, new Rectangle2D.Double(offsetX, top, spriteWidth, spriteHeight)));
        g2.fill(new Rectangle2D.Double(0, top, width, spriteHeight));
        g2.dispose();
    }

    // ---------------------------------------------------------------- main painting
    /** Sky, water body and living swell. Drawn behind the screen content. */
    public static void paintBackdrop(Graphics2D g, int width, int height, double t) {
        if (width < 8 || height < 8) return;
        ensureLayers(width, height);
        int horizon = horizon(height);
        if (cachedWidth == width && cachedHeight == height) {
            g.drawImage(backdrop, 0, 0, null);
        } else {
            // mid-resize: stretch the previous bake so the frame stays cheap
            g.drawImage(backdrop, 0, 0, width, height, null);
            horizon = horizon(cachedHeight);
        }
        paintClouds(g, width, horizon, t);
        // swell: bands are spread over the whole water body so the motion reaches the near water
        double water = Math.max(1, height - horizon);
        double[] position = {.02, .15, .31, .50, .72};
        double[] scroll = {30, 22, 16, 11, 7};
        double[] amp = {1.4, 2.2, 3, 4, 5};
        double[] surface = {1.0, .95, .88, .80, .70};
        // smaller windows get the smaller crest sprites, so waves stay proportionate
        double reach = Math.min(1, water / 600.0);
        for (int i = 0; i < position.length; i++) {
            double bob = Math.sin(t * (0.42 - i * 0.04) + i * 1.7) * amp[i];
            int variant = (int) Math.round(i * (CREST_WIDTH.length - 1) / (double) (position.length - 1) * reach);
            band(g, variant, horizon + position[i] * water + bob, width, t * scroll[i], surface[i], (int) (t * 0.12) % 2);
        }
        paintSunPath(g, width, height, horizon, t);
        paintRays(g, width, height, horizon, t, 0.085, 3);
        paintCaustics(g, 0, horizon + 8, width, Math.max(0, height - horizon - 8), t, 0.075);
        paintBubbles(g, width, horizon + 20, height + 20, t, 16, 0.22, 41);
        paintFoamMotes(g, width, height, horizon, t, 0.22);
        paintDistantShips(g, width, horizon, t);
        paintGulls(g, width, horizon, t, 0.55, 3);
    }
    /** Light haze drawn above the content so the UI feels submerged in the same water. */
    public static void paintDrift(Graphics2D g, int width, int height, double t) {
        if (width < 8 || height < 8) return;
        paintRays(g, width, height, 0, t, 0.05, 2);
        double sweep = easeInOutSine((t % 9.0) / 9.0);
        g.setColor(new Color(198, 240, 255, 11));
        Path2D glitter = new Path2D.Double();
        glitter.moveTo(-width * 0.3 + sweep * width * 1.4, -40);
        glitter.lineTo(width * 0.1 + sweep * width * 1.4, -40);
        glitter.lineTo(-width * 0.5 + sweep * width * 1.4, height + 40);
        glitter.lineTo(-width * 0.9 + sweep * width * 1.4, height + 40);
        glitter.closePath(); g.fill(glitter);
        paintBottomLap(g, width, height, t, 0.75);
    }
    private static void paintClouds(Graphics2D g, int width, int horizon, double t) {
        for (int i = 0; i < 5; i++) {
            double speed = 4 + noise(i) * 7, span = width + 420;
            double x = (noise(i * 13) * span + t * speed) % span - 210;
            double y = horizon * (0.14 + noise(i * 29) * 0.72), scale = 0.5 + noise(i * 17) * 1.1;
            double puffs = 3 + (int) (noise(i * 23) * 3);
            for (int p = 0; p < puffs; p++) {
                double px = x + p * 34 * scale - puffs * 12 * scale, py = y + noise(i * 31 + p) * 10 * scale;
                double rx = (34 + noise(i + p * 41) * 46) * scale, ry = (11 + noise(i * 3 + p) * 13) * scale;
                g.setColor(new Color(202, 230, 245, 16 + (int) (noise(p * 61 + i) * 12)));
                g.fill(new Ellipse2D.Double(px - rx, py - ry, rx * 2, ry * 2));
                g.setColor(new Color(255, 226, 186, 10 + (int) (noise(p + i) * 10)));
                g.fill(new Ellipse2D.Double(px - rx * 0.8, py + ry * 0.45, rx * 1.6, ry * 0.6));
            }
        }
    }
    private static void paintSunPath(Graphics2D g, int width, int height, int horizon, double t) {
        double cx = width * 0.78, column = Math.max(30, height * 0.09);
        for (int i = 0; i < 34; i++) {
            double p = i / 34.0, y = horizon + 6 + p * (height - horizon) * 0.95;
            double wobble = Math.sin(t * 1.4 + i * 0.9) * 5 * p, spread = column * (0.3 + p * 1.5);
            double x = cx + wobble - spread / 2;
            g.setColor(new Color(255, 218, 158, (int) (44 * (1 - p) + (Math.sin(t * 2.2 + i) > 0.2 ? 34 : 8))));
            g.fill(new Rectangle2D.Double(x, y, spread * (0.5 + Math.abs(Math.sin(t * 0.9 + i * 0.7)) * 0.5), 2 + p * 2));
        }
    }
    /** God rays are a cached sprite, so three of them cost a few blits instead of three big fills. */
    private static void paintRays(Graphics2D g, int width, int height, int horizon, double t, double alpha, int count) {
        BufferedImage sprite = raySprite();
        for (int i = 0; i < count; i++) {
            double sway = Math.sin(t * 0.28 + i * 1.9) * width * 0.05;
            double flicker = alpha / 0.085 * (0.62 + 0.38 * Math.sin(t * 0.5 + i));
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, flicker))));
            g2.drawImage(sprite, (int) (width * (0.14 + i * 0.33) + sway), (int) (horizon - height * 0.06), null);
            g2.dispose();
        }
    }
    /** A leaf-shaped shaft of light: it tapers to nothing at both ends, so no hard edges show. */
    private static BufferedImage raySprite() {
        if (ray != null) return ray;
        int width = 210, height = 520, mid = width / 2;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Theme.graphics(image);
        for (int layer = 0; layer < 3; layer++) {
            double spread = 0.42 + layer * 0.16, alpha = 46 / (layer + 1.0);
            Path2D band = new Path2D.Double();
            band.moveTo(mid - width * 0.10, 0);
            band.quadTo(mid - width * spread, height * 0.34, mid - width * 0.05, height);
            band.lineTo(mid + width * 0.05, height);
            band.quadTo(mid + width * spread * 0.9, height * 0.34, mid + width * 0.10, 0);
            band.closePath();
            g.setPaint(new GradientPaint(0, 0, new Color(214, 245, 255, (int) alpha), 0, height * 0.9f, new Color(214, 245, 255, 0)));
            g.fill(band);
        }
        g.dispose();
        return ray = image;
    }
    /** Shifting light net, as seen through clear water: one seamless tile, scrolled as a blit. */
    public static void paintCaustics(Graphics2D g, int x, int y, int width, int height, double t, double alpha) {
        if (width <= 0 || height <= 0) return;
        BufferedImage sprite = causticSprite();
        int size = sprite.getWidth();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0.05, Math.min(1, alpha / 0.20))));
        g2.setPaint(new TexturePaint(sprite, new Rectangle2D.Double(-(t * 5.5 % size), -(t * 2.4 % size), size, size)));
        g2.fillRect(x, y, width, height);
        g2.dispose();
    }
    private static BufferedImage causticSprite() {
        if (caustics != null) return caustics;
        int size = 256;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Theme.graphics(image);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int band = 0; band < 5; band++) {
            double offset = band * size / 5.0, tilt = 0.40 + noise(band * 5) * 0.16;
            for (int wrap = -1; wrap <= 1; wrap++) {
                g.setColor(new Color(198, 242, 255, (int) (20 + noise(band * 7) * 18)));
                Path2D line = new Path2D.Double();
                for (int step = 0; step <= 8; step++) {
                    double py = step * size / 8.0, px = offset + wrap * size + py * tilt + Math.sin(2 * Math.PI * step / 8.0 + band) * size * 0.06;
                    if (step == 0) line.moveTo(px, py); else line.lineTo(px, py);
                }
                g.draw(line);
                g.setColor(new Color(226, 250, 255, (int) (9 + noise(band * 11) * 10)));
                Path2D cross = new Path2D.Double();
                for (int step = 0; step <= 6; step++) {
                    double px = step * size / 6.0, py = offset * 1.3 + wrap * size + px * (0.62 - tilt) - size * 0.2;
                    if (step == 0) cross.moveTo(px, py); else cross.lineTo(px, py);
                }
                g.draw(cross);
            }
        }
        for (int i = 0; i < 14; i++) {
            double cx = noise(i * 3) * size, cy = noise(i * 9) * size, r = size * (0.06 + noise(i * 5) * 0.12);
            g.setColor(new Color(226, 250, 255, 10));
            for (int wx = -1; wx <= 1; wx++) for (int wy = -1; wy <= 1; wy++)
                g.fill(new Ellipse2D.Double(cx + wx * size - r, cy + wy * size - r, r * 2, r * 2));
        }
        g.dispose();
        return caustics = image;
    }
    public static void paintBubbles(Graphics2D g, int width, double fromY, double toY, double t, int count, double alpha, int seed) {
        for (int i = 0; i < count; i++) {
            double speed = 14 + noise(seed + i * 3) * 34, phase = (noise(seed + i) + t * speed / 120.0) % 1.0;
            double y = lerp(toY, fromY, phase), x = width * (0.03 + noise(seed + i * 7) * 0.94) + Math.sin(t * 0.9 + i) * 8;
            double radius = 1.6 + noise(seed + i * 11) * 3.4;
            double fade = Math.sin(Math.PI * phase);
            g.setColor(new Color(222, 248, 255, (int) (alpha * 255 * fade)));
            g.fill(new Ellipse2D.Double(x, y, radius * 2, radius * 2));
            g.setColor(new Color(255, 255, 255, (int) (alpha * 190 * fade)));
            g.fill(new Ellipse2D.Double(x + radius * 0.35, y + radius * 0.25, radius * 0.6, radius * 0.6));
        }
    }
    private static void paintFoamMotes(Graphics2D g, int width, int height, int horizon, double t, double alpha) {
        for (int i = 0; i < 18; i++) {
            double x = (noise(i * 5) * width + t * (5 + noise(i) * 12)) % (width + 40) - 20;
            double y = horizon + noise(i * 19) * (height - horizon), size = 1.5 + noise(i * 23) * 3.4;
            g.setColor(new Color(236, 252, 255, (int) (alpha * 190 * (0.3 + 0.7 * Math.abs(Math.sin(t * 0.8 + i))))));
            g.fill(new Ellipse2D.Double(x, y, size, size));
        }
    }
    private static void paintDistantShips(Graphics2D g, int width, int horizon, double t) {
        for (int i = 0; i < 2; i++) {
            double x = width * (0.55 + i * 0.13), bob = Math.sin(t * 0.7 + i * 2.1) * 1.8, scale = 0.5 + i * 0.25;
            paintShipSilhouette(g, x, horizon + 3 + bob, scale, new Color(8, 34, 54, 210), new Color(180, 220, 235, 120));
            g.setColor(new Color(226, 246, 255, 40));
            g.fill(new Rectangle2D.Double(x - 16 * scale, horizon + 2 + bob, 34 * scale, 1.6));
        }
    }
    public static void paintGulls(Graphics2D g, int width, int horizon, double t, double alpha, int count) {
        for (int i = 0; i < count; i++) {
            double speed = 16 + noise(i * 9) * 22, span = width + 200;
            double x = (noise(i * 3) * span + t * speed) % span - 100;
            double y = horizon * (0.22 + noise(i * 7) * 0.4) + Math.sin(t * 1.1 + i * 2.4) * 7;
            double flap = Math.sin(t * (5 + noise(i) * 2.4) + i) * (5 + noise(i * 11) * 4);
            double size = 5 + noise(i * 13) * 4;
            g.setColor(new Color(232, 248, 255, (int) (alpha * 220)));
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new QuadCurve2D.Double(x - size * 2, y + flap * 0.4, x - size * 0.6, y - flap, x, y));
            g.draw(new QuadCurve2D.Double(x, y, x + size * 0.6, y - flap, x + size * 2, y + flap * 0.4));
        }
    }
    /** Foam crawling along the bottom edge, so the sea laps at the base of the screen. */
    private static void paintBottomLap(Graphics2D g, int width, int height, double t, double alpha) {
        int base = height - 10;
        Path2D lap = new Path2D.Double();
        for (int i = 0; i <= 48; i++) {
            double x = i * width / 48.0, y = base + 5 * Math.sin(2 * Math.PI * 2 * i / 48.0 + t * 0.8) + 3 * Math.sin(2 * Math.PI * 5 * i / 48.0 - t * 1.3);
            if (i == 0) lap.moveTo(x, y); else lap.lineTo(x, y);
        }
        Path2D body = (Path2D) lap.clone(); body.lineTo(width, height); body.lineTo(0, height); body.closePath();
        g.setPaint(new GradientPaint(0, base - 6, new Color(198, 240, 255, 0), 0, height, new Color(216, 246, 255, (int) (alpha * 150))));
        g.fill(body);
        g.setColor(new Color(240, 253, 255, (int) (alpha * 210)));
        g.setStroke(new BasicStroke(1.5f)); g.draw(lap);
        for (int i = 0; i < 14; i++) {
            double x = (noise(i * 3) * width + t * (8 + noise(i) * 16)) % (width + 30) - 15;
            double y = height - 8 - noise(i * 7) * 9, size = 1.6 + noise(i * 5) * 3;
            g.setColor(new Color(248, 254, 255, (int) (alpha * 170)));
            g.fill(new Ellipse2D.Double(x, y, size, size * 0.8));
        }
    }
    /** A tiny seascape strip: used as decoration inside narrow headers. */
    public static void paintMiniSeascape(Graphics2D g, int width, int height, double t) {
        if (width < 4 || height < 4) return;
        int horizon = Math.max(2, (int) (height * 0.52));
        g.setPaint(new GradientPaint(0, 0, Theme.SEA_SKY_LOW, 0, horizon, alpha(Theme.SEA_MID, 220)));
        g.fillRect(0, 0, width, horizon + 1);
        g.setPaint(new GradientPaint(0, horizon, Theme.SEA_MID, 0, height, Theme.SEA_DEEP));
        g.fillRect(0, horizon, width, height - horizon);
        g.setColor(alpha(Theme.SEA_SUN, 190));
        double sun = Math.max(3, height * 0.14), cx = width * 0.82, cy = horizon - sun * 0.6;
        g.fill(new Ellipse2D.Double(cx - sun, cy - sun, sun * 2, sun * 2));
        for (int i = 0; i < 3; i++) {
            Path2D crest = new Path2D.Double();
            double base = horizon + 2 + i * (height - horizon) * 0.3, amp = 1.2 + i;
            for (int s = 0; s <= 32; s++) {
                double x = s * width / 32.0, y = base + amp * Math.sin(2 * Math.PI * (1 + i) * s / 32.0 + t * (0.9 + i * 0.3));
                if (s == 0) crest.moveTo(x, y); else crest.lineTo(x, y);
            }
            g.setColor(new Color(226, 246, 255, 26 + i * 12));
            g.setStroke(new BasicStroke(1.2f)); g.draw(crest);
        }
        paintShipSilhouette(g, width * 0.3, horizon + height * 0.16, Math.max(0.25, height / 90.0), new Color(7, 30, 48, 220), new Color(190, 226, 240, 150));
    }

    // ---------------------------------------------------------------- decorations
    public static void paintShipSilhouette(Graphics2D g, double x, double y, double scale, Color body, Color accent) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y); g2.scale(scale, scale);
        Path2D hull = new Path2D.Double();
        hull.moveTo(-26, 0); hull.lineTo(-16, 8); hull.lineTo(14, 8); hull.lineTo(26, -1); hull.lineTo(18, -4); hull.lineTo(-24, -4); hull.closePath();
        g2.setColor(body); g2.fill(hull);
        g2.fill(new Rectangle2D.Double(-12, -12, 20, 8));
        g2.fill(new Rectangle2D.Double(-6, -18, 9, 6));
        g2.fill(new Rectangle2D.Double(6, -11, 5, 7));
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(new Line2D.Double(-9, -12, -9, -22));
        g2.draw(new Line2D.Double(-9, -22, -1, -19));
        g2.setColor(accent); g2.draw(new Line2D.Double(-24, -3, 24, -3));
        g2.dispose();
    }
    /** Expanding sonar rings, as seen on a surface-search console. */
    public static void paintSonar(Graphics2D g, double cx, double cy, double maxRadius, double elapsed, double period,
                                  Color color, int rings, double alpha) {
        if (elapsed <= 0) return;
        g.setColor(alpha(color, Math.max(0, Math.min(255, alpha * 255))));
        g.setStroke(new BasicStroke(1.3f));
        double step = maxRadius * 0.32;
        for (int i = 0; i < rings; i++) {
            double phase = (elapsed / period + i / (double) rings) % 1.0;
            double radius = phase * maxRadius;
            if (radius < 3) continue;
            g.setColor(alpha(color, Math.max(0, Math.min(255, alpha * 255 * (1 - phase)))));
            g.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
        }
        double sweep = elapsed % period / period;
        if (sweep < 0.14) {
            g.setColor(alpha(color, Math.max(0, Math.min(255, alpha * 150 * (1 - sweep / 0.14)))));
            g.draw(new Ellipse2D.Double(cx - step, cy - step, step * 2, step * 2));
        }
    }
    /** Splash rings left on the water by a shot. */
    public static void paintRipple(Graphics2D g, double cx, double cy, double elapsed, double maxRadius) {
        if (elapsed < 0 || elapsed > 2.6) return;
        double p = clamp01(elapsed / 2.6);
        for (int i = 0; i < 3; i++) {
            double phase = clamp01(p - i * 0.14); if (phase <= 0) continue;
            double radius = phase * maxRadius * (1 - i * 0.22), fade = (1 - phase) * (1 - i * 0.25);
            g.setColor(alpha(Theme.SEA_FOAM, 160 * fade));
            g.setStroke(new BasicStroke((float) Math.max(1, 2.4 * (1 - phase))));
            g.draw(new Ellipse2D.Double(cx - radius, cy - radius * 0.72, radius * 2, radius * 1.44));
        }
        int drops = (int) (12 * (1 - p));
        for (int i = 0; i < drops; i++) {
            double angle = noise(i * 13) * Math.PI * 2, distance = 6 + (1 - p) * maxRadius * (0.4 + noise(i * 7) * 0.8);
            double dx = cx + Math.cos(angle) * distance, dy = cy + Math.sin(angle) * distance * 0.66 - Math.sin(p * Math.PI) * 16;
            double size = 2 + noise(i * 3) * 3;
            g.setColor(alpha(Theme.SEA_FOAM, 200 * (1 - p)));
            g.fill(new Ellipse2D.Double(dx, dy, size, size));
        }
    }
    public static void paintCompassRose(Graphics2D g, double cx, double cy, double radius, double t) {
        g.setColor(alpha(Theme.SEA_HORIZON, 150));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
        g.setColor(alpha(Theme.SEA_FOAM, 90));
        g.draw(new Ellipse2D.Double(cx - radius * 0.72, cy - radius * 0.72, radius * 1.44, radius * 1.44));
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16, length = i % 4 == 0 ? radius * 0.24 : radius * 0.12;
            g.setColor(alpha(Theme.SEA_FOAM, i % 4 == 0 ? 170 : 90));
            g.draw(new Line2D.Double(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius,
                    cx + Math.cos(angle) * (radius - length), cy + Math.sin(angle) * (radius - length)));
        }
        double heading = Math.sin(t * 0.22) * 0.32 - Math.PI / 2;
        Path2D needle = new Path2D.Double();
        needle.moveTo(cx + Math.cos(heading) * radius * 0.62, cy + Math.sin(heading) * radius * 0.62);
        needle.lineTo(cx + Math.cos(heading + 2.4) * radius * 0.16, cy + Math.sin(heading + 2.4) * radius * 0.16);
        needle.lineTo(cx + Math.cos(heading - 2.4) * radius * 0.16, cy + Math.sin(heading - 2.4) * radius * 0.16);
        needle.closePath();
        g.setColor(alpha(Theme.DANGER, 210)); g.fill(needle);
        Path2D tail = new Path2D.Double();
        tail.moveTo(cx - Math.cos(heading) * radius * 0.62, cy - Math.sin(heading) * radius * 0.62);
        tail.lineTo(cx + Math.cos(heading + 2.4) * radius * 0.16, cy + Math.sin(heading + 2.4) * radius * 0.16);
        tail.lineTo(cx + Math.cos(heading - 2.4) * radius * 0.16, cy + Math.sin(heading - 2.4) * radius * 0.16);
        tail.closePath();
        g.setColor(alpha(Theme.SEA_FOAM, 190)); g.fill(tail);
        g.setColor(alpha(Theme.SEA_FOAM, 230)); g.fill(new Ellipse2D.Double(cx - radius * 0.1, cy - radius * 0.1, radius * 0.2, radius * 0.2));
        g.setFont(Theme.FONT_GRID.deriveFont(Font.BOLD, Math.max(8f, (float) radius * 0.34f)));
        String[] marks = {"B", "Đ", "N", "T"};
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2 - Math.PI / 2;
            double x = cx + Math.cos(angle) * radius * 0.86, y = cy + Math.sin(angle) * radius * 0.86;
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(i == 0 ? alpha(Theme.WARNING, 235) : alpha(Theme.SEA_FOAM, 190));
            g.drawString(marks[i], (float) x - metrics.stringWidth(marks[i]) / 2f, (float) y + metrics.getAscent() / 3f);
        }
    }
    /** Animated water line used as a section rule. */
    public static void paintWaveRule(Graphics2D g, int x, int y, int width, double t, Color color, double alpha) {
        Path2D line = new Path2D.Double();
        for (int i = 0; i <= 40; i++) {
            double px = x + width * i / 40.0, py = y + 2.4 * Math.sin(2 * Math.PI * 3 * i / 40.0 + t * 1.1) + 1.4 * Math.sin(2 * Math.PI * 7 * i / 40.0 - t * 1.7);
            if (i == 0) line.moveTo(px, py); else line.lineTo(px, py);
        }
        g.setColor(alpha(color, alpha * 255));
        g.setStroke(new BasicStroke(1.3f)); g.draw(line);
    }

    // ---------------------------------------------------------------- entrance effect
    /**
     * "Cast off": a breaking wave crosses the screen while a sonar ping and the mission plate
     * announce the new phase. Painted over the content, never opaque enough to hide it.
     */
    public static void paintIntro(Graphics2D g, int width, int height, double elapsed, String title, String subtitle) {
        if (elapsed < 0 || elapsed >= INTRO_SECONDS) return;
        double fill = clamp01(elapsed / 0.50);
        double bubbles = clamp01(elapsed / 0.95);
        double sweep = clamp01((elapsed - 0.10) / 1.05);
        double plate = clamp01((elapsed - 0.40) / 0.75);
        double leave = clamp01((elapsed - 1.72) / 0.68);
        double rings = clamp01((elapsed - 0.50) / 1.55);
        double focus = clamp01((elapsed - 0.02) / 0.25) * (1 - clamp01((elapsed - 1.15) / 0.85)) * 0.30;
        if (focus > 0.001) { g.setColor(alpha(Theme.SEA_ABYSS, focus * 255)); g.fillRect(0, 0, width, height); }
        if (fill < 1) {
            float radius = (float) Math.max(width, height) * (0.35f + (float) fill * 0.7f);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(width / 2.0, height * 0.5), radius, new float[] {0f, 1f},
                    new Color[] {alpha(Theme.SEA_FOAM, 130 * (1 - fill)), alpha(Theme.SEA_FOAM, 0)}));
            g.fill(new Ellipse2D.Double(width / 2.0 - radius, height * 0.5 - radius, radius * 2, radius * 2));
        }
        if (bubbles < 1) paintBubbles(g, width, -20, height + 30, elapsed * 3.4, 46, 0.34 * (1 - bubbles), 77);
        if (sweep > 0 && sweep < 1) {
            double band = height * 0.46, travel = easeInOutSine(sweep);
            double crestY = height + band - travel * (height + band * 2);
            Path2D crest = new Path2D.Double();
            for (int i = 0; i <= 60; i++) {
                double x = i * width / 60.0, y = crestY + 16 * Math.sin(2 * Math.PI * 2 * i / 60.0 + elapsed * 2.4) + 9 * Math.sin(2 * Math.PI * 5 * i / 60.0 - elapsed * 3.1);
                if (i == 0) crest.moveTo(x, y); else crest.lineTo(x, y);
            }
            Path2D body = (Path2D) crest.clone(); body.lineTo(width, crestY + band); body.lineTo(0, crestY + band); body.closePath();
            Graphics2D gb = (Graphics2D) g.create();
            gb.setPaint(new GradientPaint(0, (float) crestY, new Color(58, 156, 186, 120), 0, (float) (crestY + band), alpha(Theme.SEA_DEEP, 20)));
            gb.fill(body);
            gb.setColor(alpha(Theme.SEA_FOAM, 210)); gb.setStroke(new BasicStroke(2.4f)); gb.draw(crest);
            for (int i = 0; i < 30; i++) {
                double x = i * width / 30.0, y = crestY + 16 * Math.sin(2 * Math.PI * 2 * i / 30.0 + elapsed * 2.4);
                gb.setColor(alpha(Theme.SEA_FOAM, 150 * (1 - sweep)));
                gb.fill(new Ellipse2D.Double(x, y - 6 - noise(i * 5) * 10, 3 + noise(i) * 4, 3 + noise(i * 3) * 4));
            }
            gb.dispose();
        }
        if (plate > 0) {
            double slide = easeOutBack(plate), out = easeOutCubic(leave);
            double plateWidth = Math.min(width * 0.64, 660), plateHeight = Math.min(168, height * 0.30);
            double cx = width / 2.0, cy = height * 0.46 - out * 70 + (1 - slide) * 110 + Math.sin(elapsed * 1.9) * 4;
            double fade = 1 - out;
            Graphics2D gb = (Graphics2D) g.create();
            gb.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, fade))));
            double left = cx - plateWidth / 2, top = cy - plateHeight / 2;
            gb.setColor(new Color(2, 12, 26, 130));
            gb.fillRoundRect((int) left + 3, (int) top + 7, (int) plateWidth, (int) plateHeight, 26, 26);
            gb.setColor(alpha(Theme.SEA_GLASS, 226));
            gb.fillRoundRect((int) left, (int) top, (int) plateWidth, (int) plateHeight, 26, 26);
            gb.setPaint(new GradientPaint(0, (float) top, new Color(255, 255, 255, 34), 0, (float) (top + plateHeight * 0.7), new Color(255, 255, 255, 0)));
            gb.fillRoundRect((int) left + 1, (int) top + 1, (int) plateWidth - 1, (int) plateHeight - 1, 25, 25);
            gb.setColor(alpha(Theme.SEA_HORIZON, 120)); gb.setStroke(new BasicStroke(1.4f));
            gb.drawRoundRect((int) left, (int) top, (int) plateWidth, (int) plateHeight, 26, 26);
            double waterY = top + plateHeight * 0.70;
            paintWaveRule(gb, (int) left + 14, (int) waterY, (int) plateWidth - 28, elapsed, Theme.SEA_FOAM, 60);
            paintWaveRule(gb, (int) left + 14, (int) waterY + 5, (int) plateWidth - 28, elapsed * 1.4 + 2, Theme.SEA_HORIZON, 40);
            double sail = (elapsed - 0.55) / 1.35;
            if (sail > 0 && sail < 1.05) {
                double shipX = left + 26 + sail * (plateWidth - 52), shipY = waterY + 9 + Math.sin(elapsed * 3) * 1.6;
                paintShipSilhouette(gb, shipX, shipY, 0.95, alpha(Theme.BG_DARK, 235), alpha(Theme.SEA_FOAM, 190));
                for (int i = 0; i < 7; i++) {
                    double wake = shipX - 14 - i * 7, drift = Math.sin(elapsed * 2 + i) * 1.4;
                    gb.setColor(alpha(Theme.SEA_FOAM, 150 - i * 18));
                    gb.fill(new Ellipse2D.Double(wake, shipY + 2 + drift, 4 - i * 0.35, 2 - i * 0.2));
                }
            }
            double compass = Math.max(20, plateHeight * 0.30);
            paintCompassRose(gb, left + compass + 22, top + plateHeight / 2.0, compass, elapsed);
            paintSonar(gb, left + plateWidth - compass - 30, top + plateHeight / 2.0, compass * 1.5, elapsed - 0.5, 1.1, Theme.SEA_HORIZON, 3, 0.55);
            Font titleFont = Theme.FONT_TITLE.deriveFont(Font.BOLD, Math.max(24f, Math.min(48f, (float) plateWidth / 14f)));
            FontMetrics titleMetrics = gb.getFontMetrics(titleFont);
            int titleX = (int) (cx - titleMetrics.stringWidth(title) / 2.0), titleY = (int) (top + plateHeight * 0.40);
            gb.setFont(titleFont);
            gb.setColor(new Color(2, 12, 26, 200)); gb.drawString(title, titleX + 2, titleY + 2);
            gb.setPaint(new GradientPaint(titleX, titleY - titleMetrics.getAscent(), Theme.SEA_FOAM, titleX, titleY + titleMetrics.getDescent(), new Color(122, 226, 226)));
            gb.drawString(title, titleX, titleY);
            Font subFont = Theme.FONT_BOLD.deriveFont(14f);
            FontMetrics subMetrics = gb.getFontMetrics(subFont);
            gb.setFont(subFont); gb.setColor(alpha(Theme.SEA_HORIZON, 235));
            gb.drawString(subtitle, (int) (cx - subMetrics.stringWidth(subtitle) / 2.0), (int) (top + plateHeight * 0.58));
            gb.dispose();
        }
        if (rings > 0) {
            double fade = 1 - easeOutCubic(leave);
            paintSonar(g, width / 2.0, height * 0.46, Math.max(width, height) * 0.60, elapsed - 0.50, 0.78, Theme.SEA_FOAM, 3, 0.42 * fade);
        }
        if (elapsed > 0.45) paintGulls(g, width, Math.max(120, (int) (height * 0.40)), elapsed - 0.45, 0.6 * (1 - easeOutCubic(leave)), 4);
    }
}
