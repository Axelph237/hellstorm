package dev.candycanecazoo.hellstorm.client.anim;

/**
 * A CSS timing function: how far along a change is (0 to 1) at a point in its time (0 to 1). The constants are CSS's
 * keywords, with {@link #cubicBezier} and {@link #steps} for the rest.
 */
@FunctionalInterface
public interface Easing {
    float apply(float t);

    Easing LINEAR = t -> t;
    Easing EASE = cubicBezier(0.25f, 0.1f, 0.25f, 1f);
    Easing EASE_IN = cubicBezier(0.42f, 0f, 1f, 1f);
    Easing EASE_OUT = cubicBezier(0f, 0f, 0.58f, 1f);
    Easing EASE_IN_OUT = cubicBezier(0.42f, 0f, 0.58f, 1f);

    /** CSS {@code cubic-bezier(x1, y1, x2, y2)}: the curve from (0, 0) to (1, 1) through those two control points. */
    static Easing cubicBezier(float x1, float y1, float x2, float y2) {
        return t -> {
            if (t <= 0 || t >= 1) {
                return Math.max(0, Math.min(1, t));
            }
            // Find where on the curve x is t (x only grows, so bisection always finds it), then read y there.
            float low = 0;
            float high = 1;
            float s = t;
            for (int i = 0; i < 24; i++) {
                float x = bezier(s, x1, x2);
                if (Math.abs(x - t) < 1e-5f) {
                    break;
                }
                if (x < t) {
                    low = s;
                } else {
                    high = s;
                }
                s = (low + high) / 2;
            }
            return bezier(s, y1, y2);
        };
    }

    /** One coordinate of a cubic Bezier from 0 to 1 with control points {@code p1} and {@code p2}. */
    private static float bezier(float s, float p1, float p2) {
        float u = 1 - s;
        return 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s;
    }

    /** CSS {@code steps(n)} ({@code jump-end}): {@code n} even jumps, each at the end of its step. */
    static Easing steps(int n) {
        return t -> t >= 1 ? 1 : (float) Math.floor(Math.max(0, t) * n) / n;
    }
}
