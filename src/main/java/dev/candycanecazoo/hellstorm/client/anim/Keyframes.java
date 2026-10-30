package dev.candycanecazoo.hellstorm.client.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A CSS {@code @keyframes} rule: values at offsets from 0 to 1, each with the timing function for the stretch to the
 * next ({@code animation-timing-function} on a keyframe). Missing 0 or 1 frames take the element's own value there, as
 * in CSS. Built with {@link #at}.
 */
public final class Keyframes<T> {
    /** One keyframe. An empty {@code easing} uses the animation's own. */
    private record Frame<T>(float offset, T value, Optional<Easing> easing) {}

    private final Interpolator<T> interpolator;
    private final List<Frame<T>> frames = new ArrayList<>();

    public Keyframes(Interpolator<T> interpolator) {
        this.interpolator = interpolator;
    }

    public static Keyframes<Float> floats() {
        return new Keyframes<>(Interpolator.FLOAT);
    }

    public static Keyframes<Integer> colors() {
        return new Keyframes<>(Interpolator.ARGB);
    }

    /** {@code value} at {@code offset} (0 to 1, as CSS's 0% to 100%). */
    public Keyframes<T> at(float offset, T value) {
        return add(offset, value, Optional.empty());
    }

    /** {@code value} at {@code offset}, eased by {@code easing} on the way to the next keyframe. */
    public Keyframes<T> at(float offset, T value, Easing easing) {
        return add(offset, value, Optional.of(easing));
    }

    private Keyframes<T> add(float offset, T value, Optional<Easing> easing) {
        frames.add(new Frame<>(Math.max(0, Math.min(1, offset)), value, easing));
        frames.sort(Comparator.comparingDouble(Frame::offset));
        return this;
    }

    /** The value at {@code progress} through the keyframes (0 to 1), with {@code base} where a 0 or 1 frame is missing. */
    T sample(float progress, T base, Easing easing) {
        List<Frame<T>> all = new ArrayList<>(frames);
        if (all.isEmpty() || all.get(0).offset() > 0) {
            all.add(0, new Frame<>(0, base, Optional.empty()));
        }
        if (all.get(all.size() - 1).offset() < 1) {
            all.add(new Frame<>(1, base, Optional.empty()));
        }
        for (int i = 0; i < all.size() - 1; i++) {
            Frame<T> a = all.get(i);
            Frame<T> b = all.get(i + 1);
            if (progress <= b.offset() || i == all.size() - 2) {
                float span = b.offset() - a.offset();
                float local = span <= 0 ? 1 : Math.max(0, Math.min(1, (progress - a.offset()) / span));
                return interpolator.lerp(a.value(), b.value(), a.easing().orElse(easing).apply(local));
            }
        }
        return all.get(all.size() - 1).value();
    }
}
