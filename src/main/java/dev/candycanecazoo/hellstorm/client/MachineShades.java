package dev.candycanecazoo.hellstorm.client;

import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Optional;

/**
 * Repaints a texture's green in another color, keeping its shading. Each green shade is placed relative to the median
 * green: its hue is turned by the same angle from the target's hue, and its saturation and brightness sit the same way
 * above or below the target's as they did the median's, stretched to stay in range. Colors are packed {@code 0xRRGGBB}.
 */
public final class MachineShades {
    /** What counts as the machine's green. The shades painted measure hue 89 to 142, saturation 0.33 up, brightness 0.47 up. */
    private static final float MIN_HUE = 75 / 360f;
    private static final float MAX_HUE = 155 / 360f;
    private static final float MIN_SATURATION = 0.25f;
    private static final float MIN_BRIGHTNESS = 0.3f;

    /** Hue as a turn in [0, 1), saturation and brightness in [0, 1]. */
    public record Hsv(float h, float s, float v) {
        public static Hsv of(int rgb) {
            float r = (rgb >> 16 & 0xff) / 255f;
            float g = (rgb >> 8 & 0xff) / 255f;
            float b = (rgb & 0xff) / 255f;
            float max = Math.max(r, Math.max(g, b));
            float range = max - Math.min(r, Math.min(g, b));
            float h;
            if (range == 0) {
                h = 0;
            } else if (max == r) {
                h = (g - b) / range / 6;
            } else if (max == g) {
                h = ((b - r) / range + 2) / 6;
            } else {
                h = ((r - g) / range + 4) / 6;
            }
            return new Hsv(h - (float) Math.floor(h), max == 0 ? 0 : range / max, max);
        }

        public int rgb() {
            return Mth.hsvToRgb(h, s, v);
        }
    }

    private MachineShades() {}

    public static boolean isGreen(int rgb) {
        Hsv c = Hsv.of(rgb);
        return c.h() >= MIN_HUE && c.h() <= MAX_HUE && c.s() >= MIN_SATURATION && c.v() >= MIN_BRIGHTNESS;
    }

    /** The median hue, saturation, and brightness of the green pixels, each taken on its own; empty if there are none. */
    public static Optional<Hsv> median(int[] pixels) {
        int[] green = Arrays.stream(pixels).filter(MachineShades::isGreen).toArray();
        if (green.length == 0) {
            return Optional.empty();
        }
        float[][] parts = new float[3][green.length];
        for (int i = 0; i < green.length; i++) {
            Hsv c = Hsv.of(green[i]);
            parts[0][i] = c.h();
            parts[1][i] = c.s();
            parts[2][i] = c.v();
        }
        return Optional.of(new Hsv(middle(parts[0]), middle(parts[1]), middle(parts[2])));
    }

    private static float middle(float[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    /** {@code rgb} repainted for {@code target}, if it's green; any other color comes back as it was. */
    public static int recolor(int rgb, Hsv median, Hsv target) {
        if (!isGreen(rgb)) {
            return rgb;
        }
        Hsv c = Hsv.of(rgb);
        float h = target.h() + c.h() - median.h();
        return new Hsv(h - (float) Math.floor(h), stretch(c.s(), median.s(), target.s()), stretch(c.v(), median.v(), target.v())).rgb();
    }

    /** Takes [0, from] onto [0, to] and [from, 1] onto [to, 1], so {@code from} lands on {@code to} and the order holds. */
    static float stretch(float x, float from, float to) {
        if (x <= from) {
            return from == 0 ? to : to * x / from;
        }
        return to + (1 - to) * (x - from) / (1 - from);
    }
}
