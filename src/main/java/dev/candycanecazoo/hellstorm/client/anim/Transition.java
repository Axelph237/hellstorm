package dev.candycanecazoo.hellstorm.client.anim;

import java.util.Objects;

/**
 * A value that changes smoothly, like a CSS property with a {@code transition}: give it a new target and it eases from
 * wherever it is now to there over {@code durationMillis}, after {@code delayMillis}. Changing the target midway starts
 * the new change from the value then, so nothing jumps. Times are in milliseconds, from {@link UiClock} in game.
 */
public final class Transition<T> {
    private final Interpolator<T> interpolator;
    private final long durationMillis;
    private final Easing easing;
    private final long delayMillis;
    private T from;
    private T to;
    private long startedAt = Long.MIN_VALUE;

    public Transition(Interpolator<T> interpolator, T initial, long durationMillis, Easing easing, long delayMillis) {
        this.interpolator = interpolator;
        this.durationMillis = durationMillis;
        this.easing = easing;
        this.delayMillis = delayMillis;
        this.from = initial;
        this.to = initial;
    }

    /** A number transition with no delay. */
    public static Transition<Float> of(float initial, long durationMillis, Easing easing) {
        return new Transition<>(Interpolator.FLOAT, initial, durationMillis, easing, 0);
    }

    /** Heads for {@code target} from the value at {@code now}; nothing changes if it's already the target. */
    public void set(T target, long now) {
        if (Objects.equals(target, to)) {
            return;
        }
        from = value(now);
        to = target;
        startedAt = now;
    }

    public void set(T target) {
        set(target, UiClock.now());
    }

    /** Goes straight to {@code target}, with no transition. */
    public void snap(T target) {
        from = target;
        to = target;
        startedAt = Long.MIN_VALUE;
    }

    public T target() {
        return to;
    }

    public T value(long now) {
        if (startedAt == Long.MIN_VALUE) {
            return to;
        }
        long elapsed = now - startedAt - delayMillis;
        if (elapsed <= 0) {
            return from;
        }
        if (durationMillis <= 0 || elapsed >= durationMillis) {
            return to;
        }
        return interpolator.lerp(from, to, easing.apply((float) elapsed / durationMillis));
    }

    public T value() {
        return value(UiClock.now());
    }
}
