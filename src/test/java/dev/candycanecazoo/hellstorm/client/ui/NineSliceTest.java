package dev.candycanecazoo.hellstorm.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NineSliceTest {
    /** The input box: 12-pixel corners, its edge pieces 4 pixels away from them on the sheet. */
    private static final NineSlice BOX = new NineSlice(82, 82, 44, 12, 4);

    @Test
    void aLargeBoxKeepsItsCornersAndStretchesTheEdgePiecesSkippingTheGaps() {
        List<NineSlice.Piece> pieces = BOX.pieces(10, 20, 100, 40);
        assertEquals(9, pieces.size());
        assertEquals(new NineSlice.Piece(10, 20, 12, 12, 82, 82, 12, 12), pieces.get(0));
        assertEquals(new NineSlice.Piece(22, 20, 76, 12, 98, 82, 12, 12), pieces.get(1));
        assertEquals(new NineSlice.Piece(98, 20, 12, 12, 114, 82, 12, 12), pieces.get(2));
        assertEquals(new NineSlice.Piece(10, 32, 12, 16, 82, 98, 12, 12), pieces.get(3));
        assertEquals(new NineSlice.Piece(98, 48, 12, 12, 114, 114, 12, 12), pieces.get(8));
    }

    @Test
    void aBoxUnderTwiceTheCornerCutsEachCornerToHalfKeepingItsOuterSide() {
        List<NineSlice.Piece> pieces = BOX.pieces(0, 0, 20, 20);
        assertEquals(new NineSlice.Piece(0, 0, 10, 10, 82, 82, 10, 10), pieces.get(0));
        assertEquals(new NineSlice.Piece(10, 0, 10, 10, 116, 82, 10, 10), pieces.get(1));
        assertEquals(new NineSlice.Piece(10, 10, 10, 10, 116, 116, 10, 10), pieces.get(3));
    }

    @Test
    void anOddSizeFillsTheLeftoverPixelFromTheEdgePiece() {
        List<NineSlice.Piece> pieces = BOX.pieces(0, 0, 21, 20);
        assertEquals(new NineSlice.Piece(10, 0, 1, 10, 98, 82, 12, 10), pieces.get(1));
        assertEquals(new NineSlice.Piece(11, 0, 10, 10, 116, 82, 10, 10), pieces.get(2));
    }
}
