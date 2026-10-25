package dev.candycanecazoo.hellstorm.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A square region of a sprite sheet drawn at any size: its corners stay as drawn, its edges and middle stretch. Below
 * twice the corner size each corner is cut to half the target, keeping its outer side, so small boxes stay closed.
 */
public record NineSlice(int u, int v, int size, int corner) {
    /** Where one piece of the sheet lands on screen. */
    public record Piece(int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {}

    public List<Piece> pieces(int x, int y, int width, int height) {
        int cornerX = Math.min(corner, width / 2);
        int cornerY = Math.min(corner, height / 2);
        int[] xs = {x, x + cornerX, x + width - cornerX};
        int[] widths = {cornerX, width - 2 * cornerX, cornerX};
        int[] us = {u, u + corner, u + size - cornerX};
        int[] uWidths = {cornerX, size - 2 * corner, cornerX};
        int[] ys = {y, y + cornerY, y + height - cornerY};
        int[] heights = {cornerY, height - 2 * cornerY, cornerY};
        int[] vs = {v, v + corner, v + size - cornerY};
        int[] vHeights = {cornerY, size - 2 * corner, cornerY};
        List<Piece> pieces = new ArrayList<>(9);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (widths[column] > 0 && heights[row] > 0) {
                    pieces.add(new Piece(xs[column], ys[row], widths[column], heights[row], us[column], vs[row], uWidths[column], vHeights[row]));
                }
            }
        }
        return pieces;
    }
}
