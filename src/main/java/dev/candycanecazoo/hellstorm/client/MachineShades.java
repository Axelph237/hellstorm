package dev.candycanecazoo.hellstorm.client;

import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Optional;

/**
 * Repaints the vending machine around a new basis color, keeping its shading. The basis is the median of the machine's
 * green, and it becomes the target color. Each color is repainted by its {@link Role}: the brand shades follow the target,
 * the purple-tinted greys turn greyer the further the target is from green, and the whites and lights keep their colors, as
 * do the keypad's red and green lights. Colors are packed {@code 0xRRGGBB}.
 */
public final class MachineShades {
    /** What counts as the machine's green, for the basis. The shades painted measure hue 89 to 142, saturation 0.33 up, brightness 0.47 up. */
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

    /** What a color does on the machine, told from its hue, saturation, and brightness. */
    public enum Role {
        /** The machine's own color: the greens, lighter shades leaning toward yellow and darker ones toward blue. */
        BRAND,
        /** Greys and near-blacks, tinted purple to read as grey beside green. */
        GREY,
        /** The DRINK letters, the counter, the highlights. */
        WHITE,
        /** The cyan glow of the screen strip, the top bar, and the lit glass. */
        LIGHT
    }

    private static final float MAX_WHITE_SATURATION = 0.13f;
    private static final float MIN_WHITE_BRIGHTNESS = 0.78f;
    private static final float MIN_LIGHT_HUE = 170 / 360f;
    private static final float MAX_LIGHT_HUE = 215 / 360f;
    private static final float MIN_LIGHT_BRIGHTNESS = 0.85f;
    /** Toward this hue is cooler, away from it warmer. */
    private static final float BLUE = 240 / 360f;
    /** The greys keep their saturation with a green target and fall to {@link #FARTHEST_GREY} of it from this far off. */
    private static final float GREY_FADE = 60 / 360f;
    private static final float FARTHEST_GREY = 0.4f;

    private MachineShades() {}

    public static Role role(int rgb) {
        if (isGreen(rgb)) {
            return Role.BRAND;
        }
        Hsv c = Hsv.of(rgb);
        if (c.s() <= MAX_WHITE_SATURATION && c.v() >= MIN_WHITE_BRIGHTNESS) {
            return Role.WHITE;
        }
        if (c.h() >= MIN_LIGHT_HUE && c.h() <= MAX_LIGHT_HUE && c.s() >= MIN_SATURATION && c.v() >= MIN_LIGHT_BRIGHTNESS) {
            return Role.LIGHT;
        }
        return Role.GREY;
    }

    /**
     * The keypad, in pixels of one 128-pixel frame of either texture: its lights are two green pixels and three red ones
     * (the red blinks lighter on the lit strip), inside this box.
     */
    private static final int KEYPAD_MIN_X = 55;
    private static final int KEYPAD_MAX_X = 62;
    private static final int KEYPAD_MIN_Y = 58;
    private static final int KEYPAD_MAX_Y = 62;
    private static final float MAX_RED_HUE = 20 / 360f;
    private static final float MIN_RED_HUE = 330 / 360f;

    public static boolean isGreen(int rgb) {
        Hsv c = Hsv.of(rgb);
        return c.h() >= MIN_HUE && c.h() <= MAX_HUE && c.s() >= MIN_SATURATION && c.v() >= MIN_BRIGHTNESS;
    }

    private static boolean isRed(int rgb) {
        Hsv c = Hsv.of(rgb);
        return (c.h() >= MIN_RED_HUE || c.h() <= MAX_RED_HUE) && c.s() >= MIN_SATURATION && c.v() >= MIN_BRIGHTNESS;
    }

    /** Whether the pixel at {@code (x, frameY)} of a frame is one of the keypad's red or green lights. */
    public static boolean keypadLight(int x, int frameY, int rgb) {
        return x >= KEYPAD_MIN_X && x <= KEYPAD_MAX_X && frameY >= KEYPAD_MIN_Y && frameY <= KEYPAD_MAX_Y && (isGreen(rgb) || isRed(rgb));
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

    /** {@code rgb} repainted around the new basis, {@code median} becoming {@code target}. */
    public static int recolor(int rgb, Hsv median, Hsv target) {
        Hsv c = Hsv.of(rgb);
        return switch (role(rgb)) {
            case BRAND -> {
                // A shade cooler than the median stays as much cooler than the target, whichever way cooler is from there.
                float h = target.h() + turn(median.h(), c.h()) * coolward(median.h()) * coolward(target.h());
                yield new Hsv(h - (float) Math.floor(h), stretch(c.s(), median.s(), target.s()), stretch(c.v(), median.v(), target.v())).rgb();
            }
            case GREY -> {
                float away = Math.min(1, Math.abs(turn(median.h(), target.h())) / GREY_FADE);
                yield new Hsv(c.h(), c.s() * (1 - (1 - FARTHEST_GREY) * away), c.v()).rgb();
            }
            case WHITE, LIGHT -> rgb;
        };
    }

    /** The shortest turn from hue {@code from} to hue {@code to}, in (-0.5, 0.5]. */
    static float turn(float from, float to) {
        float d = to - from;
        return d - (float) Math.ceil(d - 0.5f);
    }

    /** 1 if blue is reached from {@code hue} by turning up, -1 if by turning down. */
    private static int coolward(float hue) {
        return turn(hue, BLUE) >= 0 ? 1 : -1;
    }

    /** Takes [0, from] onto [0, to] and [from, 1] onto [to, 1], so {@code from} lands on {@code to} and the order holds. */
    static float stretch(float x, float from, float to) {
        if (x <= from) {
            return from == 0 ? to : to * x / from;
        }
        return to + (1 - to) * (x - from) / (1 - from);
    }
}
