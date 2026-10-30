package dev.candycanecazoo.hellstorm.client.hud;

import dev.candycanecazoo.hellstorm.client.hud.ProgressBar.Piece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgressBarTest {
    @Test
    void aWholeRowIsItsTwoEndsAndAStretchedMiddle() {
        assertEquals(List.of(new Piece(10, 3, 0, 3), new Piece(13, 94, 3, 18), new Piece(107, 3, 21, 3)), ProgressBar.pieces(10, 100, 100));
    }

    @Test
    void aPartRowStopsAtItsProgressAndShowsThatMuchOfTheMiddle() {
        assertEquals(List.of(new Piece(0, 3, 0, 3), new Piece(3, 47, 3, 9)), ProgressBar.pieces(0, 100, 50));
    }

    @Test
    void anEmptyRowIsNothingAndAShortOneIsPartOfItsEnd() {
        assertEquals(List.of(), ProgressBar.pieces(0, 100, 0));
        assertEquals(List.of(new Piece(0, 2, 0, 2)), ProgressBar.pieces(0, 100, 2));
    }
}
