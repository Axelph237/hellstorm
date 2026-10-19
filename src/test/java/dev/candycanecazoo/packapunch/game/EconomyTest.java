package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyTest {
    private static final GameSettings.Economy E = new GameSettings.Economy(500, 10, 60, 100, 130);

    @Test
    void eachSourceAwardsItsSetting() {
        assertEquals(10, Economy.award(E, new PointSource.Hit(), false));
        assertEquals(60, Economy.award(E, new PointSource.Kill(false, false), false));
        assertEquals(100, Economy.award(E, new PointSource.Kill(true, false), false));
        assertEquals(130, Economy.award(E, new PointSource.Kill(false, true), false));
        assertEquals(130, Economy.award(E, new PointSource.Kill(true, true), false), "a melee kill pays melee even on the head");
    }

    @Test
    void doublePointsDoublesEverything() {
        assertEquals(20, Economy.award(E, new PointSource.Hit(), true));
        assertEquals(200, Economy.award(E, new PointSource.Kill(true, false), true));
    }
}
