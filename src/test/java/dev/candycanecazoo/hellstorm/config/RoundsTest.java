package dev.candycanecazoo.hellstorm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundsTest {
    private static int ticks(int ms) {
        GameSettings.Rounds d = GameSettings.DEFAULT.rounds();
        return new GameSettings.Rounds(d.baseCount(), d.aiPerPlayer(), d.healthScale(), d.healthGrowth(), d.runFromRound(), d.sprintFromRound(),
                d.walkSpeed(), d.runSpeed(), d.sprintSpeed(), d.maxAlive(), d.spawnDelayTicks(), d.spawnDelayFactor(), ms, d.countdownTicks(), d.gameOverTicks())
                .intermissionTicks();
    }

    @Test
    void theBreakBetweenRoundsIsMillisecondsRoundedUpToTicks() {
        assertEquals(3000, GameSettings.DEFAULT.rounds().intermissionMs(), "three seconds by default");
        assertEquals(60, ticks(3000));
        assertEquals(0, ticks(0));
        assertEquals(1, ticks(1), "a short break still lasts a tick");
        assertEquals(2, ticks(51));
    }
}
