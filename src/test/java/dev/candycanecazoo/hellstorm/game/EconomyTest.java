package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyTest {
    private static final GameSettings.Economy E = new GameSettings.Economy(500, 10, 60, 100, 130, 10, 500, 100);

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

    @Test
    void aRepairPaysOnlyWhatIsLeftUnderTheWindowAndRoundCaps() {
        assertEquals(10, Economy.repairPay(E, 10, 0, 0));
        assertEquals(10, Economy.repairPay(E, 10, 90, 90), "the board that reaches the window cap still pays");
        assertEquals(0, Economy.repairPay(E, 10, 100, 100), "a window at its cap pays nothing");
        assertEquals(10, Economy.repairPay(E, 10, 100, 0), "another window still pays");
        assertEquals(5, Economy.repairPay(E, 20, 95, 95), "a doubled board pays only what's left under the window cap");
        assertEquals(5, Economy.repairPay(E, 10, 495, 0), "the round cap still bounds every window");
        assertEquals(0, Economy.repairPay(E, 10, 500, 0));
    }
}
