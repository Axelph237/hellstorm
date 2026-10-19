package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;

/** The per-round numbers, as pure functions of the round, the player count, and the settings. */
public final class RoundPlan {
    private RoundPlan() {}

    public static int zombieCount(GameSettings.Rounds s, int round, int players) {
        double base = s.baseCount() + s.countPerRound() * (round - 1);
        return Math.max(1, (int) Math.round(base * (1 + s.playerFactor() * Math.max(0, players - 1))));
    }

    /** CoD's curve (150 + 100 per round to round 9, then x1.1 per round) scaled into Minecraft HP. */
    public static double zombieHealth(GameSettings.Rounds s, int round) {
        double cod = round < 10 ? 150 + 100.0 * (round - 1) : 950 * Math.pow(1.1, round - 9);
        return Math.max(1.0, cod * s.healthScale());
    }

    /** Share of zombies that sprint: none before {@code sprintFromRound}, then 20% more each round. */
    public static double sprintChance(GameSettings.Rounds s, int round) {
        return Math.min(1.0, Math.max(0.0, (round - s.sprintFromRound() + 1) * 0.2));
    }

    public static int spawnDelayTicks(GameSettings.Rounds s, int round) {
        return Math.max(5, s.spawnDelayTicks() - 2 * (round - 1));
    }
}
