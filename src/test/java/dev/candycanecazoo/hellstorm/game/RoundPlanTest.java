package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The defaults are Black Ops' own numbers. */
class RoundPlanTest {
    private static final GameSettings.Rounds R = GameSettings.DEFAULT.rounds();

    private static List<Integer> counts(int players, int toRound) {
        return IntStream.rangeClosed(1, toRound).map(round -> RoundPlan.zombieCount(R, round, players)).boxed().toList();
    }

    @Test
    void soloCountsAreCodsFirstTenRounds() {
        assertEquals(List.of(6, 8, 13, 18, 24, 27, 28, 28, 29, 33), counts(1, 10));
    }

    @Test
    void morePlayersAddSixEachScaledByTheRound() {
        assertEquals(10, RoundPlan.zombieCount(R, 1, 4), "(24 + 3 x 6) x 25%");
        assertEquals(45, RoundPlan.zombieCount(R, 6, 4), "24 + int(3 x 6 x 1.2) = 24 + 21; 1.2 from round 6 / 5");
        assertEquals(24 + (int) (6 * 3.0), RoundPlan.zombieCount(R, 10, 2), "round 10: 2 x (10 x 0.15) = 3");
        assertEquals(24 + (int) (0.5 * 6 * (20 / 5.0) * (20 * 0.15)), RoundPlan.zombieCount(R, 20, 1), "round 20 solo: 24 + 36 = 60");
    }

    @Test
    void healthIsCodsCurve() {
        assertEquals(22.5, RoundPlan.zombieHealth(R, 1), 1e-9, "150 x 0.15");
        assertEquals(142.5, RoundPlan.zombieHealth(R, 9), 1e-9, "950 x 0.15");
        assertEquals(950 * 1.1 * 0.15, RoundPlan.zombieHealth(R, 10), 1e-9, "10% more each round from round 10");
        assertEquals(950 * Math.pow(1.1, 21) * 0.15, RoundPlan.zombieHealth(R, 30), 1e-6);
    }

    @Test
    void walkersThenRunnersFromRoundFiveThenSprintersFromRoundNine() {
        assertEquals(R.walkSpeed(), RoundPlan.zombieSpeed(R, 4));
        assertEquals(R.runSpeed(), RoundPlan.zombieSpeed(R, 5));
        assertEquals(R.runSpeed(), RoundPlan.zombieSpeed(R, 8));
        assertEquals(R.sprintSpeed(), RoundPlan.zombieSpeed(R, 9));
        assertEquals(R.sprintSpeed(), RoundPlan.zombieSpeed(R, 50), "no further growth");
    }

    @Test
    void theSpawnDelayShrinksFivePercentARoundToCodsFloor() {
        assertEquals(40, RoundPlan.spawnDelayTicks(R, 1), "2 seconds");
        assertEquals((int) Math.round(40 * Math.pow(0.95, 9)), RoundPlan.spawnDelayTicks(R, 10));
        assertEquals(RoundPlan.MIN_SPAWN_DELAY_TICKS, RoundPlan.spawnDelayTicks(R, 100));
    }
}
