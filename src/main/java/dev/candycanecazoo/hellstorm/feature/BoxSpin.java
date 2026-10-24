package dev.candycanecazoo.hellstorm.feature;

import java.util.Optional;

/**
 * A roll's guns rising out of the crate, timed by the client that draws them. The client's game time can't time it: the
 * server resets it once a second, so behind a busy server or a jittery connection it jumps back and forth, and the guns
 * flick backward with it. Instead the server says how far into the roll it is when it sends the roll, and from there the
 * client counts its own ticks.
 */
public final class BoxSpin {
    private static final long NONE = Long.MIN_VALUE;

    private long roll = NONE;
    private int duration;
    private int elapsed;

    /**
     * The server sent roll {@code roll}, {@code elapsed} ticks into its {@code duration}. The same roll sent again (its price
     * changed, say) leaves the clock running: catching up to the server would be the very jump this avoids.
     */
    public void receive(long roll, int duration, int elapsed) {
        if (roll != this.roll) {
            this.roll = roll;
            this.duration = duration;
            this.elapsed = elapsed;
        }
    }

    public void stop() {
        roll = NONE;
    }

    /** One client tick. */
    public void tick() {
        if (roll != NONE) {
            elapsed++;
        }
    }

    /** How far the guns have risen, {@code partialTick} past the last client tick. Empty without a roll. */
    public Optional<Float> rise(float partialTick) {
        return roll == NONE ? Optional.empty() : rise(elapsed + partialTick, duration);
    }

    /**
     * How far the guns have risen {@code elapsed} ticks into a roll of {@code duration}, 0 to 1, slowing as they near the
     * top. Empty while the lid opens, its first {@link MysteryBoxBlock#LID_TICKS}.
     */
    public static Optional<Float> rise(float elapsed, int duration) {
        if (elapsed < MysteryBoxBlock.LID_TICKS) {
            return Optional.empty();
        }
        float risen = Math.min(1, (elapsed - MysteryBoxBlock.LID_TICKS) / Math.max(1, duration - MysteryBoxBlock.LID_TICKS));
        return Optional.of(1 - (1 - risen) * (1 - risen));
    }

    /** Which flick of the reel shows at {@code rise}: 0 to {@link MysteryBoxBlock#FLICKS}. */
    public static int flick(float rise) {
        return (int) (rise * MysteryBoxBlock.FLICKS);
    }
}
