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
    void healthFollowsCodsShapeThenGrowsSlowly() {
        assertEquals(22.5, RoundPlan.zombieHealth(R, 1), 1e-9, "150 x 0.15");
        assertEquals(82.5, RoundPlan.zombieHealth(R, 5), 1e-9, "(150 + 400) x 0.15");
        assertEquals(142.5, RoundPlan.zombieHealth(R, 9), 1e-9);
        assertEquals(950 * 1.004 * 0.15, RoundPlan.zombieHealth(R, 10), 1e-9, "0.4% more than round 9");
        assertEquals(950 * Math.pow(1.004, 41) * 0.15, RoundPlan.zombieHealth(R, 50), 1e-9, "about 168 HP by round 50");
    }

    @Test
    void speedGrowsByAHundredthOfAPercentPerRound() {
        assertEquals(0.23, RoundPlan.zombieSpeed(R, 1, false), 1e-12);
        assertEquals(0.23 * 1.0001, RoundPlan.zombieSpeed(R, 2, false), 1e-12);
        assertEquals(0.32 * Math.pow(1.0001, 49), RoundPlan.zombieSpeed(R, 50, true), 1e-12, "sprinters grow at the same rate");
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
