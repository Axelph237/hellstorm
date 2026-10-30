package dev.candycanecazoo.hellstorm.client.anim;

/** How a kind of value is blended between two others, as CSS does for each animatable property type. */
@FunctionalInterface
public interface Interpolator<T> {
    /** {@code from} at 0, {@code to} at 1. Eased timing can take {@code t} a little past either end. */
    T lerp(T from, T to, float t);

    Interpolator<Float> FLOAT = (from, to, t) -> from + (to - from) * t;

    /** An ARGB color, each channel blended on its own and kept in range. */
    Interpolator<Integer> ARGB = (from, to, t) -> {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = from >>> shift & 0xff;
            int b = to >>> shift & 0xff;
            out |= Math.max(0, Math.min(255, Math.round(a + (b - a) * t))) << shift;
        }
        return out;
    };
}
