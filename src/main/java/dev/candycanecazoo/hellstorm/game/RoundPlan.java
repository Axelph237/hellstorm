package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;

/** Black Ops' per-round numbers (from its {@code _zombiemode} script), as pure functions of the round, the player count, and the settings. */
public final class RoundPlan {
    /** CoD's spawn delay floor, 0.08 seconds, in whole ticks. */
    static final int MIN_SPAWN_DELAY_TICKS = 2;
    /** Rounds 1 to 5 get this share of the full count, easing a game in. */
    private static final double[] EARLY_ROUNDS = {0.25, 0.3, 0.5, 0.7, 0.9};

    private RoundPlan() {}

    /**
     * {@code base_count}, plus {@code ai_per_player} for each player past the first (half of it alone), the extra scaled by
     * the round: round / 5 (at least 1), and from round 10 times round x 0.15 as well. Solo: 6, 8, 13, 18, 24, 27, 28,
     * 28, 29, 33 for rounds 1 to 10.
     */
    public static int zombieCount(GameSettings.Rounds s, int round, int players) {
        double multiplier = Math.max(1.0, round / 5.0);
        if (round >= 10) {
            multiplier *= round * 0.15;
        }
        int extra = players <= 1 ? (int) (0.5 * s.aiPerPlayer() * multiplier) : (int) ((players - 1) * s.aiPerPlayer() * multiplier);
        double share = round >= 1 && round <= EARLY_ROUNDS.length ? EARLY_ROUNDS[round - 1] : 1.0;
        return Math.max(1, (int) ((s.baseCount() + extra) * share));
    }

    /** CoD's health: 150 + 100 per round up to round 9, then growing {@code health_growth} per round, compounding (CoD's own is 10%). Times {@code health_scale} into Minecraft HP. */
    public static double zombieHealth(GameSettings.Rounds s, int round) {
        double cod = round < 10 ? 150 + 100.0 * (round - 1) : 950 * Math.pow(1 + s.healthGrowth(), round - 9);
        return Math.max(1.0, cod * s.healthScale());
    }

    /** Walkers, then runners from {@code run_from_round}, then sprinters from {@code sprint_from_round} (CoD: 5 and 9). */
    public static double zombieSpeed(GameSettings.Rounds s, int round) {
        if (round >= s.sprintFromRound()) {
            return s.sprintSpeed();
        }
        return round >= s.runFromRound() ? s.runSpeed() : s.walkSpeed();
    }

    /** {@code spawn_delay_ticks}, times {@code spawn_delay_factor} each round after the first, down to CoD's floor. */
    public static int spawnDelayTicks(GameSettings.Rounds s, int round) {
        return Math.max(MIN_SPAWN_DELAY_TICKS, (int) Math.round(s.spawnDelayTicks() * Math.pow(s.spawnDelayFactor(), round - 1)));
    }
}
