package dev.candycanecazoo.hellstorm.client.ui;

import dev.candycanecazoo.hellstorm.client.anim.Animation;
import dev.candycanecazoo.hellstorm.client.anim.Easing;
import dev.candycanecazoo.hellstorm.client.anim.Keyframes;

/**
 * Things that come in one after another, top first: each runs {@code in} from 0 to 1, {@code stagger} milliseconds after
 * the one before it, and stays at 0 until its turn.
 */
public record Ladder(Animation<Float> in, long stagger) {
    /** A ladder of {@code duration}-long eased-out entrances, {@code stagger} apart. */
    public static Ladder of(long duration, long stagger) {
        return new Ladder(Animation.of(Keyframes.floats().at(0, 0f).at(1, 1f), duration).withEasing(Easing.EASE_OUT)
                .withFill(Animation.Fill.BOTH), stagger);
    }

    /** How far in the {@code index}th thing is (0 to 1), {@code elapsed} milliseconds after the ladder started. */
    public float progress(long elapsed, int index) {
        return in.sample(elapsed - index * stagger, 1f);
    }

    /** How long until the last of {@code count} things is all the way in. */
    public long length(int count) {
        return in.duration() + Math.max(0, count - 1) * stagger;
    }
}
