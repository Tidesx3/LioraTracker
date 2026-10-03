import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Builds the end-of-rest sounds from scratch, so they're Liora's own (no licences) and can be tuned here:
 * - chime: two vibraphone notes rising a fourth (G5, C6), the second the louder, like "ready"
 * - bell: a small bell struck twice, the ring of a round about to start
 * - marimba: three quick wooden notes up a major chord (E5, G5, C6)
 *
 * Each note is a struck bar: a few partials at the bar's own ratios, each dying away, the high ones
 * fastest. Fundamentals sit between 650 and 1600 Hz and their overtones reach 3 to 4 kHz, where a phone
 * speaker is loud and gym noise thin. A short room reverb rounds them off. Writes 44.1 kHz 16-bit mono WAV
 * files into the given res/raw folder and a sheet of their waveforms to check by eye: run
 * `java tools/sound/MakeSounds.java app/android/src/main/res/raw build/sound-preview.png`.
 */
public class MakeSounds {
    static final int RATE = 44_100;

    /** One partial of a struck bar: its frequency as a multiple of the note's, its level, and how fast it dies away. */
    record Partial(double ratio, double level, double decaySeconds) {}

    // A vibraphone bar is tuned so its first overtone sits two octaves up; a slight second harmonic warms it.
    static final Partial[] VIBRAPHONE = {
        new Partial(1.0, 1.0, 0.65), new Partial(2.0, 0.06, 0.4), new Partial(4.0, 0.22, 0.18), new Partial(9.9, 0.035, 0.05),
    };

    // A small bell's partials aren't harmonic, which is what makes it a bell; the pairs a hair apart beat slowly.
    static final Partial[] BELL = {
        new Partial(1.0, 1.0, 0.85), new Partial(1.0012, 0.18, 0.85), new Partial(2.32, 0.45, 0.45),
        new Partial(2.3228, 0.08, 0.45), new Partial(4.25, 0.22, 0.22), new Partial(6.63, 0.1, 0.1),
    };

    // A marimba bar: the fundamental, a tuned overtone just under two octaves up, and a quick high one.
    static final Partial[] MARIMBA = {
        new Partial(1.0, 1.0, 0.42), new Partial(3.93, 0.32, 0.09), new Partial(9.2, 0.07, 0.025),
    };

    public static void main(String[] args) throws IOException {
        File raw = new File(args[0]);
        File preview = new File(args[1]);
        raw.mkdirs();
        double[] chime = new double[seconds(1.8)];
        strike(chime, 0.0, 783.99, 0.8, VIBRAPHONE, 0.0025);
        strike(chime, 0.13, 1046.50, 1.0, VIBRAPHONE, 0.0025);
        double[] bell = new double[seconds(2.1)];
        strike(bell, 0.0, 1480.0, 0.85, BELL, 0.001);
        strike(bell, 0.3, 1480.0, 1.0, BELL, 0.001);
        double[] marimba = new double[seconds(1.3)];
        double[] notes = {659.25, 783.99, 1046.50};
        for (int i = 0; i < notes.length; i++) {
            strike(marimba, i * 0.085, notes[i], 0.85 + 0.075 * i, MARIMBA, 0.001);
            mallet(marimba, i * 0.085, 0.06, i);
        }
        double[][] sounds = {finish(chime), finish(bell), finish(marimba)};
        String[] names = {"rest_chime", "rest_bell", "rest_marimba"};
        for (int i = 0; i < sounds.length; i++) writeWav(sounds[i], new File(raw, names[i] + ".wav"));
        writePreview(sounds, names, preview);
    }

    static int seconds(double s) {
        return (int) Math.round(s * RATE);
    }

    /** Adds a note struck at [at] seconds: the partials rise over [attack] seconds, then die away. */
    static void strike(double[] out, double at, double frequency, double level, Partial[] partials, double attack) {
        int start = seconds(at);
        for (Partial p : partials) {
            double f = frequency * p.ratio;
            if (f >= RATE / 2.0) continue;
            for (int n = start; n < out.length; n++) {
                double t = (n - start) / (double) RATE;
                double envelope = Math.exp(-t / p.decaySeconds) * Math.min(1.0, t / attack);
                if (envelope < 1e-5 && t > attack) break;
                out[n] += level * p.level * envelope * Math.sin(2 * Math.PI * f * t);
            }
        }
    }

    /** The mallet's knock: a few milliseconds of soft, low-passed noise. */
    static void mallet(double[] out, double at, double level, long seed) {
        Random random = new Random(seed);
        int start = seconds(at);
        double smooth = 0;
        for (int n = start; n < Math.min(out.length, start + seconds(0.02)); n++) {
            double t = (n - start) / (double) RATE;
            smooth += 0.25 * (random.nextGaussian() - smooth);
            out[n] += level * smooth * Math.exp(-t / 0.003);
        }
    }

    /** Room, then a level that peaks just under full scale, and a fade so the end doesn't click. */
    static double[] finish(double[] dry) {
        double[] out = reverb(dry, 0.16);
        double peak = 0;
        for (double v : out) peak = Math.max(peak, Math.abs(v));
        double gain = 0.89 / peak;
        int fade = seconds(0.15);
        for (int n = 0; n < out.length; n++) {
            out[n] *= gain;
            int left = out.length - n;
            if (left < fade) out[n] *= 0.5 - 0.5 * Math.cos(Math.PI * left / fade);
        }
        return out;
    }

    /** A small room after Schroeder and Freeverb: four damped combs in parallel, then two all-passes. */
    static double[] reverb(double[] dry, double wet) {
        int[] combs = {1557, 1617, 1491, 1422};
        int[] allPasses = {556, 225};
        double feedback = 0.74;
        double damping = 0.3;
        double[] room = new double[dry.length];
        for (int delay : combs) {
            double[] buffer = new double[delay];
            double filtered = 0;
            for (int n = 0; n < dry.length; n++) {
                double delayed = buffer[n % delay];
                filtered = delayed * (1 - damping) + filtered * damping;
                buffer[n % delay] = dry[n] + filtered * feedback;
                room[n] += delayed / combs.length;
            }
        }
        for (int delay : allPasses) {
            double[] buffer = new double[delay];
            for (int n = 0; n < room.length; n++) {
                double delayed = buffer[n % delay];
                double in = room[n];
                buffer[n % delay] = in + delayed * 0.5;
                room[n] = delayed - in;
            }
        }
        double[] out = new double[dry.length];
        for (int n = 0; n < dry.length; n++) out[n] = dry[n] + wet * room[n];
        return out;
    }

    static void writeWav(double[] samples, File file) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (double v : samples) {
            int s = (int) Math.round(Math.max(-1, Math.min(1, v)) * 32767);
            data.write(s & 0xff);
            data.write((s >> 8) & 0xff);
        }
        byte[] pcm = data.toByteArray();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("RIFF".getBytes());
            int32(out, 36 + pcm.length);
            out.write("WAVEfmt ".getBytes());
            int32(out, 16);
            int16(out, 1); // PCM
            int16(out, 1); // mono
            int32(out, RATE);
            int32(out, RATE * 2);
            int16(out, 2);
            int16(out, 16);
            out.write("data".getBytes());
            int32(out, pcm.length);
            out.write(pcm);
        }
    }

    static void int32(FileOutputStream out, int v) throws IOException {
        for (int i = 0; i < 4; i++) out.write((v >> (8 * i)) & 0xff);
    }

    static void int16(FileOutputStream out, int v) throws IOException {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
    }

    /** Each sound's waveform on one row, on a shared time scale with a line every 100 ms. */
    static void writePreview(double[][] sounds, String[] names, File file) throws IOException {
        int width = 1400, row = 220;
        BufferedImage image = new BufferedImage(width, row * sounds.length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, image.getHeight());
        int longest = 0;
        for (double[] s : sounds) longest = Math.max(longest, s.length);
        double perPixel = longest / (double) width;
        for (int i = 0; i < sounds.length; i++) {
            int mid = i * row + row / 2;
            g.setColor(new Color(0xEEEEF4));
            for (int ms = 0; ms * RATE / 1000 < longest; ms += 100) {
                int x = (int) (ms * RATE / 1000 / perPixel);
                g.drawLine(x, i * row, x, (i + 1) * row);
            }
            g.setColor(new Color(0xFFB4AB));
            g.drawLine(0, mid - (int) (row / 2 * 0.89), width, mid - (int) (row / 2 * 0.89));
            g.drawLine(0, mid + (int) (row / 2 * 0.89), width, mid + (int) (row / 2 * 0.89));
            g.setColor(new Color(0x5504CF));
            for (int x = 0; x < width; x++) {
                int from = (int) (x * perPixel), to = Math.min(sounds[i].length, (int) ((x + 1) * perPixel));
                double lo = 0, hi = 0;
                for (int n = from; n < to; n++) {
                    lo = Math.min(lo, sounds[i][n]);
                    hi = Math.max(hi, sounds[i][n]);
                }
                g.drawLine(x, mid - (int) (hi * row / 2), x, mid - (int) (lo * row / 2));
            }
            g.setColor(Color.BLACK);
            g.drawString(names[i] + String.format("  %.2f s", sounds[i].length / (double) RATE), 8, i * row + 16);
        }
        file.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
    }
}
