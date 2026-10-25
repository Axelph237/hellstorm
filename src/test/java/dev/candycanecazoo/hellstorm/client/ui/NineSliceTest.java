package dev.candycanecazoo.hellstorm.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NineSliceTest {
    private static final NineSlice BOX = new NineSlice(82, 82, 44, 16);

    @Test
    void aLargeBoxKeepsItsCornersAndStretchesTheMiddle() {
        List<NineSlice.Piece> pieces = BOX.pieces(10, 20, 100, 40);
        assertEquals(9, pieces.size());
        assertEquals(new NineSlice.Piece(10, 20, 16, 16, 82, 82, 16, 16), pieces.get(0));
        assertEquals(new NineSlice.Piece(26, 20, 68, 16, 98, 82, 12, 16), pieces.get(1));
        assertEquals(new NineSlice.Piece(94, 44, 16, 16, 110, 110, 16, 16), pieces.get(8));
    }

    @Test
    void aBoxUnderTwiceTheCornerCutsEachCornerToHalfKeepingItsOuterSide() {
        List<NineSlice.Piece> pieces = BOX.pieces(0, 0, 20, 20);
        assertEquals(List.of(
                new NineSlice.Piece(0, 0, 10, 10, 82, 82, 10, 10),
                new NineSlice.Piece(10, 0, 10, 10, 116, 82, 10, 10),
                new NineSlice.Piece(0, 10, 10, 10, 82, 116, 10, 10),
                new NineSlice.Piece(10, 10, 10, 10, 116, 116, 10, 10)), pieces);
    }

    @Test
    void anOddSizeFillsTheLeftoverPixelFromTheMiddle() {
        List<NineSlice.Piece> pieces = BOX.pieces(0, 0, 21, 20);
        assertEquals(new NineSlice.Piece(10, 0, 1, 10, 98, 82, 12, 10), pieces.get(1));
        assertEquals(new NineSlice.Piece(11, 0, 10, 10, 116, 82, 10, 10), pieces.get(2));
    }
}
