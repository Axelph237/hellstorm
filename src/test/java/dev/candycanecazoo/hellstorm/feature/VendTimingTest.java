package dev.candycanecazoo.hellstorm.feature;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VendTimingTest {
    private static List<Integer> ticks(List<VendTiming.Press> presses) {
        return presses.stream().map(VendTiming.Press::tick).toList();
    }

    @Test
    void eachClickPlaysOnTheTickItsKeyHitsBottom() {
        assertEquals(List.of(), ticks(VendTiming.pressesDue(-1, 2)));
        assertEquals(List.of(3), ticks(VendTiming.pressesDue(2, 3)));
        assertEquals(List.of(6, 9), ticks(VendTiming.pressesDue(5, 9)));
        assertEquals(List.of(3, 6, 9), ticks(VendTiming.pressesDue(-1, 31)), "a tick that skips them all plays them all once");
    }

    @Test
    void noClickPlaysTwiceWhenTimeStandsStillOrGoesBack() {
        assertEquals(List.of(), ticks(VendTiming.pressesDue(6, 6)));
        assertEquals(List.of(), ticks(VendTiming.pressesDue(9, 4)));
        assertEquals(List.of(), ticks(VendTiming.pressesDue(9, -1)), "the vend is over");
    }

    @Test
    void theThreeClicksHaveTheirOwnPitches() {
        assertEquals(List.of(1.0f, 1.12f, 0.94f), VendTiming.PRESSES.stream().map(VendTiming.Press::pitch).toList());
    }
}
