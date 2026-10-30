package dev.candycanecazoo.hellstorm.client.anim;

/**
 * A CSS {@code animation}: {@link Keyframes} played over {@code duration}, after {@code delay}, {@code iterations} times
 * ({@link #INFINITE} for ever), in a {@link Direction}, holding its first or last frame outside that time as its
 * {@link Fill} says. Built with {@link #of} and the {@code with} methods. {@link #start} gives a running one.
 */
public record Animation<T>(Keyframes<T> keyframes, long duration, Easing easing, long delay, float iterations, Direction direction, Fill fill) {
    public static final float INFINITE = Float.POSITIVE_INFINITY;

    /** CSS {@code animation-direction}. */
    public enum Direction { NORMAL, REVERSE, ALTERNATE, ALTERNATE_REVERSE }

    /** CSS {@code animation-fill-mode}: whether the first frame holds during the delay, and the last after the end. */
    public enum Fill {
        NONE(false, false), FORWARDS(false, true), BACKWARDS(true, false), BOTH(true, true);

        final boolean before;
        final boolean after;

        Fill(boolean before, boolean after) {
            this.before = before;
            this.after = after;
        }
    }

    /** Once, forwards, with CSS's default {@code ease}, no delay and no fill. */
    public static <T> Animation<T> of(Keyframes<T> keyframes, long duration) {
        return new Animation<>(keyframes, duration, Easing.EASE, 0, 1, Direction.NORMAL, Fill.NONE);
    }

    public Animation<T> withEasing(Easing easing) {
        return new Animation<>(keyframes, duration, easing, delay, iterations, direction, fill);
    }

    public Animation<T> withDelay(long delay) {
        return new Animation<>(keyframes, duration, easing, delay, iterations, direction, fill);
    }

    public Animation<T> withIterations(float iterations) {
        return new Animation<>(keyframes, duration, easing, delay, iterations, direction, fill);
    }

    public Animation<T> withDirection(Direction direction) {
        return new Animation<>(keyframes, duration, easing, delay, iterations, direction, fill);
    }

    public Animation<T> withFill(Fill fill) {
        return new Animation<>(keyframes, duration, easing, delay, iterations, direction, fill);
    }

    /** The value {@code elapsed} milliseconds after it started, or {@code base} while nothing applies. */
    public T sample(long elapsed, T base) {
        long active = elapsed - delay;
        if (active < 0) {
            return fill.before ? keyframes.sample(progress(0, 0), base, easing) : base;
        }
        double total = duration <= 0 ? iterations : (double) active / duration;
        if (total >= iterations) {
            if (!fill.after) {
                return base;
            }
            // The last frame of the last iteration, which for a whole count is the end of the one before.
            double end = iterations;
            int last = (int) Math.ceil(end) - 1;
            return keyframes.sample(progress(last, (float) (end - last)), base, easing);
        }
        int iteration = (int) Math.floor(total);
        return keyframes.sample(progress(iteration, (float) (total - iteration)), base, easing);
    }

    /** How far through the keyframes {@code into} of {@code iteration} is, after its direction. */
    private float progress(int iteration, float into) {
        boolean backwards = switch (direction) {
            case NORMAL -> false;
            case REVERSE -> true;
            case ALTERNATE -> iteration % 2 == 1;
            case ALTERNATE_REVERSE -> iteration % 2 == 0;
        };
        return backwards ? 1 - into : into;
    }

    /** Whether it's done, iterations and delay included, {@code elapsed} after it started. Never for {@link #INFINITE}. */
    public boolean finished(long elapsed) {
        return !Float.isInfinite(iterations) && elapsed - delay >= duration * (double) iterations;
    }

    public Running<T> start(long now) {
        return new Running<>(this, now);
    }

    public Running<T> start() {
        return start(UiClock.now());
    }

    /** An animation playing since {@code startedAt}. */
    public record Running<T>(Animation<T> animation, long startedAt) {
        public T value(long now, T base) {
            return animation.sample(now - startedAt, base);
        }

        public T value(T base) {
            return value(UiClock.now(), base);
        }

        public boolean finished(long now) {
            return animation.finished(now - startedAt);
        }
    }
}
