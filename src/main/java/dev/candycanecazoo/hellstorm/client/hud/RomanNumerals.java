package dev.candycanecazoo.hellstorm.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The round number in Roman numerals, laid out glyph by glyph from {@code textures/gui/hud/numeral_<value>.png}. Two
 * glyphs side by side overlap by a pixel when the first is smaller (IV, XC) or both are I (II, III); any other pair has a
 * pixel's gap between them (VI, XX, CI).
 */
public final class RomanNumerals {
    /** The glyphs there are art for. With no D, the numerals go up to 399. */
    public static final int MAX = 399;
    public static final int HEIGHT = 9;

    public enum Glyph {
        I(1, 5), V(5, 7), X(10, 7), L(50, 7), C(100, 7);

        public final int value;
        public final int width;

        Glyph(int value, int width) {
            this.value = value;
            this.width = width;
        }
    }

    /** A glyph and its left edge, in the glyphs' own pixels. */
    public record Placed(Glyph glyph, int x) {}

    public record Layout(List<Placed> glyphs, int width) {}

    private static final int[] VALUES = {100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final Glyph[][] SPELLINGS = {
            {Glyph.C}, {Glyph.X, Glyph.C}, {Glyph.L}, {Glyph.X, Glyph.L}, {Glyph.X}, {Glyph.I, Glyph.X}, {Glyph.V}, {Glyph.I, Glyph.V}, {Glyph.I}};

    private RomanNumerals() {}

    /** The glyphs for {@code n}, 1 to {@link #MAX}; nothing outside that. */
    public static Optional<List<Glyph>> glyphs(int n) {
        if (n < 1 || n > MAX) {
            return Optional.empty();
        }
        List<Glyph> glyphs = new ArrayList<>();
        int left = n;
        for (int i = 0; i < VALUES.length; i++) {
            while (left >= VALUES[i]) {
                glyphs.addAll(List.of(SPELLINGS[i]));
                left -= VALUES[i];
            }
        }
        return Optional.of(glyphs);
    }

    public static Layout layout(List<Glyph> glyphs) {
        List<Placed> placed = new ArrayList<>();
        int x = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            if (i > 0) {
                Glyph before = glyphs.get(i - 1);
                boolean overlap = before.value < glyph.value || before == Glyph.I && glyph == Glyph.I;
                x += before.width + (overlap ? -1 : 1);
            }
            placed.add(new Placed(glyph, x));
        }
        int width = placed.isEmpty() ? 0 : placed.get(placed.size() - 1).x() + placed.get(placed.size() - 1).glyph().width;
        return new Layout(placed, width);
    }
}
