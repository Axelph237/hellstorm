package dev.candycanecazoo.hellstorm.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LadderTest {
    @Test
    void eachRungStartsItsStaggerAfterTheOneBeforeAndWaitsAtZeroTillThen() {
        Ladder ladder = Ladder.of(100, 40);
        assertEquals(0, ladder.progress(0, 0), 1e-6);
        assertEquals(0, ladder.progress(39, 1), 1e-6, "the second waits for its turn");
        assertTrue(ladder.progress(60, 1) > 0, "then it starts");
        assertTrue(ladder.progress(60, 0) > ladder.progress(60, 1), "the first is always further in");
        assertEquals(1, ladder.progress(100, 0), 1e-6);
        assertEquals(1, ladder.progress(1000, 5), 1e-6, "and every one ends all the way in");
        assertEquals(100 + 3 * 40, ladder.length(4));
    }
}
