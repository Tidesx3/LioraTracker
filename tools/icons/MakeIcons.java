import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Builds the launcher and notification icons from the square logo (light artwork on black):
 * - foreground: the artwork alone, alpha from brightness, so it sits on a black background layer as in the logo
 * - monochrome: a white silhouette of the solid artwork for themed icons
 * - notification: the same silhouette, fitted into 24 dp
 */
public class MakeIcons {
    static final double CENTER_X = 630, CENTER_Y = 589;   // middle of the solid artwork
    static final double CONTENT_WIDTH = 793;              // solid artwork width in px
    static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final double[] SCALES = {1, 1.5, 2, 3, 4};

    public static void main(String[] args) throws Exception {
        BufferedImage logo = ImageIO.read(new File(args[0]));
        File res = new File(args[1]);
        File preview = new File(args[2]);
        BufferedImage artwork = artwork(logo);
        BufferedImage silhouette = silhouette(logo);
        for (int i = 0; i < DENSITIES.length; i++) {
            double s = SCALES[i];
            // Launcher: the logo in its own framing, 1254 px of logo across 76 dp.
            int canvas = (int) Math.round(108 * s);
            double k = 76.0 * s / logo.getWidth();
            write(place(artwork, canvas, k), new File(res, "mipmap-" + DENSITIES[i] + "/ic_launcher_foreground.png"));
            write(place(silhouette, canvas, k), new File(res, "mipmap-" + DENSITIES[i] + "/ic_launcher_monochrome.png"));
            // Status bar: the artwork 20 dp wide in a 24 dp icon.
            int small = (int) Math.round(24 * s);
            write(place(silhouette, small, 20.0 * s / CONTENT_WIDTH),
                new File(res, "drawable-" + DENSITIES[i] + "/ic_notification.png"));
        }
        writePreview(res, preview);
    }

    /** Light-on-black to color-with-alpha: over black it looks like the logo, elsewhere it is clear. */
    static BufferedImage artwork(BufferedImage logo) {
        BufferedImage out = new BufferedImage(logo.getWidth(), logo.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < logo.getHeight(); y++) for (int x = 0; x < logo.getWidth(); x++) {
            int c = logo.getRGB(x, y);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            int m = Math.max(r, Math.max(g, b));
            // The background noise and the lighter corner stay clear.
            double a = Math.max(0, (m - 24) / 231.0);
            if (a <= 0) { out.setRGB(x, y, 0); continue; }
            int nr = Math.min(255, r * 255 / m), ng = Math.min(255, g * 255 / m), nb = Math.min(255, b * 255 / m);
            out.setRGB(x, y, ((int) Math.round(a * 255) << 24) | (nr << 16) | (ng << 8) | nb);
        }
        return out;
    }

    /** White where the artwork is solid; the glow drops out, so small sizes stay crisp. */
    static BufferedImage silhouette(BufferedImage logo) {
        BufferedImage out = new BufferedImage(logo.getWidth(), logo.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < logo.getHeight(); y++) for (int x = 0; x < logo.getWidth(); x++) {
            int c = logo.getRGB(x, y);
            int m = Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255));
            double t = Math.min(1, Math.max(0, (m - 90) / 60.0));
            double a = t * t * (3 - 2 * t);
            out.setRGB(x, y, ((int) Math.round(a * 255) << 24) | 0xFFFFFF);
        }
        return out;
    }

    /** src scaled by k onto a square canvas, with the middle of the artwork in the middle. */
    static BufferedImage place(BufferedImage src, int canvas, double k) {
        int w = (int) Math.round(src.getWidth() * k), h = (int) Math.round(src.getHeight() * k);
        BufferedImage current = toPremultiplied(src);
        // Halve step by step first, so a large shrink averages every pixel instead of skipping most.
        while (current.getWidth() / 2 >= w) {
            BufferedImage half = new BufferedImage(current.getWidth() / 2, current.getHeight() / 2, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, half.getWidth(), half.getHeight(), null);
            g.dispose();
            current = half;
        }
        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double dx = canvas / 2.0 - CENTER_X * k, dy = canvas / 2.0 - CENTER_Y * k;
        g.drawImage(current, (int) Math.round(dx), (int) Math.round(dy), w, h, null);
        g.dispose();
        return out;
    }

    static BufferedImage toPremultiplied(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    static void write(BufferedImage img, File file) throws Exception {
        file.getParentFile().mkdirs();
        BufferedImage plain = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = plain.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        ImageIO.write(plain, "png", file);
    }

    /** The icons the way launchers and the status bar show them, to check by eye. */
    static void writePreview(File res, File preview) throws Exception {
        BufferedImage fg = ImageIO.read(new File(res, "mipmap-xxxhdpi/ic_launcher_foreground.png"));
        BufferedImage mono = ImageIO.read(new File(res, "mipmap-xxxhdpi/ic_launcher_monochrome.png"));
        BufferedImage bell = ImageIO.read(new File(res, "drawable-xxxhdpi/ic_notification.png"));
        BufferedImage bellSmall = ImageIO.read(new File(res, "drawable-mdpi/ic_notification.png"));
        BufferedImage out = new BufferedImage(1400, 380, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x6B7280));
        g.fillRect(0, 0, 1400, 380);
        // The visible part of a 108 dp adaptive icon is the middle 72 dp: 288 px at xxxhdpi.
        int y = 40, size = 288, pad = (432 - 288) / 2;
        drawIcon(g, new Ellipse2D.Double(40, y, size, size), Color.BLACK, fg, 40, y, pad);
        drawIcon(g, new RoundRectangle2D.Double(380, y, size, size, 150, 150), Color.BLACK, fg, 380, y, pad);
        // Themed icon: the system tints the monochrome layer.
        drawIcon(g, new Ellipse2D.Double(720, y, size, size), new Color(0x2B2930), tint(mono, new Color(0xD0BCFF)), 720, y, pad);
        // Status bar at xxxhdpi, then mdpi as is and blown up.
        g.setColor(new Color(0x111318));
        g.fillRect(1040, 40, 330, 120);
        g.drawImage(bell, 1050, 52, null);
        g.drawImage(bellSmall, 1160, 88, null);
        g.drawImage(bellSmall.getScaledInstance(96, 96, java.awt.Image.SCALE_REPLICATE), 1200, 52, null);
        g.dispose();
        ImageIO.write(out, "png", preview);
    }

    static void drawIcon(Graphics2D g, Shape mask, Color background, BufferedImage layer, int x, int y, int pad) {
        Shape old = g.getClip();
        g.setClip(mask);
        g.setColor(background);
        g.fill(mask);
        g.drawImage(layer, x - pad, y - pad, null);
        g.setClip(old);
    }

    static BufferedImage tint(BufferedImage img, Color color) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
            int a = img.getRGB(x, y) >>> 24;
            out.setRGB(x, y, (a << 24) | (color.getRGB() & 0xFFFFFF));
        }
        return out;
    }
}
