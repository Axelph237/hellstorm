package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundPlanTest {
    private static final GameSettings.Rounds R = GameSettings.DEFAULT.rounds();

    @Test
    void zombieCountGrowsPerRoundAndPerPlayer() {
        assertEquals(6, RoundPlan.zombieCount(R, 1, 1));
        assertEquals(16, RoundPlan.zombieCount(R, 5, 1));
        assertEquals(29, RoundPlan.zombieCount(R, 10, 1), "6 + 2.5 * 9 = 28.5, rounded");
        assertEquals(15, RoundPlan.zombieCount(R, 1, 4), "6 * (1 + 0.5 * 3)");
    }

    @Test
    void healthFollowsTheCodCurveScaled() {
        assertEquals(15.0, RoundPlan.zombieHealth(R, 1), 1e-9);
        assertEquals(95.0, RoundPlan.zombieHealth(R, 9), 1e-9);
        assertEquals(104.5, RoundPlan.zombieHealth(R, 10), 1e-9, "950 * 1.1 * 0.1");
        assertEquals(950 * Math.pow(1.1, 21) * 0.1, RoundPlan.zombieHealth(R, 30), 1e-9);
    }

    @Test
    void sprintersStartAtTheConfiguredRound() {
        assertEquals(0.0, RoundPlan.sprintChance(R, 5));
        assertEquals(0.2, RoundPlan.sprintChance(R, 6), 1e-9);
        assertEquals(1.0, RoundPlan.sprintChance(R, 20));
    }

    @Test
    void spawnDelayShrinksToAFloor() {
        assertEquals(40, RoundPlan.spawnDelayTicks(R, 1));
        assertEquals(22, RoundPlan.spawnDelayTicks(R, 10));
        assertEquals(5, RoundPlan.spawnDelayTicks(R, 50));
    }
}
