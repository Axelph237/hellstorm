package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;

/** The per-round numbers, as pure functions of the round, the player count, and the settings. */
public final class RoundPlan {
    private RoundPlan() {}

    public static int zombieCount(GameSettings.Rounds s, int round, int players) {
        double base = s.baseCount() + s.countPerRound() * (round - 1);
        return Math.max(1, (int) Math.round(base * (1 + s.playerFactor() * Math.max(0, players - 1))));
    }

    /** {@code base_health}, growing by {@code health_growth} each round after the first, compounding. */
    public static double zombieHealth(GameSettings.Rounds s, int round) {
        return Math.max(1.0, s.baseHealth() * Math.pow(1 + s.healthGrowth(), round - 1));
    }

    /** Walk or sprint speed, growing by {@code speed_growth} each round after the first, compounding. */
    public static double zombieSpeed(GameSettings.Rounds s, int round, boolean sprinting) {
        return (sprinting ? s.sprintSpeed() : s.walkSpeed()) * Math.pow(1 + s.speedGrowth(), round - 1);
    }

    /** Share of zombies that sprint: none before {@code sprintFromRound}, then 20% more each round. */
    public static double sprintChance(GameSettings.Rounds s, int round) {
        return Math.min(1.0, Math.max(0.0, (round - s.sprintFromRound() + 1) * 0.2));
    }

    public static int spawnDelayTicks(GameSettings.Rounds s, int round) {
        return Math.max(5, s.spawnDelayTicks() - 2 * (round - 1));
    }
}
