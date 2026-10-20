package dev.candycanecazoo.packapunch.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where a window's boards sit, in the plane of the opening: {@code u} runs along the wall from 0 to the window's width,
 * {@code v} up from 0 to its height. Each board spans the whole width at a slant, a little longer than the opening, and
 * board {@code i} is the {@code i}-th one nailed on (the last ones torn off first). The layout is random but seeded by
 * the window's id, so it never changes between frames, reloads, or players.
 */
public final class BoardLayout {
    /** How far a board reaches past each side of the opening. */
    public static final double OVERHANG = 0.15;
    /** Boards slant in alternate directions; the two directions sit at different depths so crossing boards don't flicker. */
    public static final int LAYERS = 2;

    /**
     * One board: centre {@code (u, v)}, {@code angle} in radians from horizontal, {@code plankRow} which of the oak planks
     * texture's four plank rows it shows, stretched along its length.
     */
    public record Board(double u, double v, double length, double width, double angle, int layer, int plankRow) {}

    private static final double MIN_ANGLE = Math.toRadians(6);
    private static final double MAX_ANGLE = Math.toRadians(24);
    private static final double EDGE = 0.04;

    private BoardLayout() {}

    public static List<Board> of(String windowId, int width, int height, int boards) {
        Random random = new Random(windowId.hashCode() * 31L + width * 7L + height);
        double boardWidth = Math.max(0.12, Math.min(0.28, height / (double) boards * 0.8));
        double band = height / (double) boards;
        double halfSpan = width / 2.0 + OVERHANG;
        List<Board> layout = new ArrayList<>(boards);
        for (int i = 0; i < boards; i++) {
            double v = band * (i + 0.5) + (random.nextDouble() - 0.5) * band * 0.4;
            v = Math.max(boardWidth / 2 + EDGE, Math.min(height - boardWidth / 2 - EDGE, v));
            // As steep as a hand-nailed board, but no steeper than keeps both ends inside the opening: boards near the
            // top and bottom lie flatter than the ones across the middle.
            double room = Math.min(v, height - v) - EDGE - boardWidth / 2;
            double steepest = Math.min(MAX_ANGLE, Math.atan(Math.max(0, room) / halfSpan));
            double angle = (i % 2 == 0 ? 1 : -1) * (Math.min(MIN_ANGLE, steepest) + random.nextDouble() * Math.max(0, steepest - MIN_ANGLE));
            double length = width / Math.cos(angle) + 2 * OVERHANG;
            double u = width / 2.0 + (random.nextDouble() - 0.5) * 0.1;
            layout.add(new Board(u, v, length, boardWidth, angle, i % LAYERS, random.nextInt(4)));
        }
        return layout;
    }
}
