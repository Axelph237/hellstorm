package dev.candycanecazoo.hellstorm.mob;

/**
 * A mob's ground speed goes with the square of its speed attribute: vanilla's {@code Mob.setSpeed} uses the attribute both
 * as the forward input and as the acceleration. On flat ground a mob walks about 43.17 x attribute² blocks a second
 * (measured by the GameTest {@code aRunnerWalksAsFastAsAWalkingPlayer}). A profile's {@code speed_multiplier} and the
 * crawler's mean "times as fast", so they scale the attribute by their square root.
 */
public final class GroundSpeed {
    private GroundSpeed() {}

    /** What to multiply the speed attribute by for a mob to move {@code timesAsFast} times as fast on the ground. */
    public static double attributeFactor(double timesAsFast) {
        return Math.sqrt(timesAsFast);
    }
}
