package dev.candycanecazoo.packapunch.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrapCycleTest {
    @Test
    void activeForItsTicksThenCoolsDownThenReady() {
        TrapCycle cycle = TrapCycle.activated(100, 30);
        assertTrue(cycle.powered());
        assertEquals(cycle, cycle.at(129, 40));
        TrapCycle cooling = cycle.at(130, 40);
        assertEquals(new TrapCycle(TrapCycle.Phase.COOLDOWN, 170), cooling);
        assertFalse(cooling.powered());
        assertEquals(cooling, cooling.at(169, 40));
        assertEquals(TrapCycle.READY, cooling.at(170, 40));
    }

    @Test
    void noCooldownGoesStraightToReady() {
        assertEquals(TrapCycle.READY, TrapCycle.activated(0, 5).at(5, 0));
    }

    @Test
    void aLateTickStillCountsCooldownFromWhenTheSignalShouldHaveEnded() {
        assertEquals(new TrapCycle(TrapCycle.Phase.COOLDOWN, 70), TrapCycle.activated(0, 30).at(35, 40));
    }

    @Test
    void secondsLeftRoundsUp() {
        assertEquals(2, new TrapCycle(TrapCycle.Phase.COOLDOWN, 121).secondsLeft(100));
        assertEquals(0, new TrapCycle(TrapCycle.Phase.COOLDOWN, 100).secondsLeft(120));
    }
}
