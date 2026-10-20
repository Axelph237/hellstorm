package dev.candycanecazoo.packapunch.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Splits a zone's standing spots into pieces a player can walk between: sideways steps, up or down one block at most.
 * A stray box drawn outside a room's walls becomes its own piece.
 */
public final class StandingArea {
    private StandingArea() {}

    /** The zone's pieces, largest first. {@code standable} says whether a player's feet fit at a cell. */
    public static List<List<BlockPos>> pieces(Zone zone, Predicate<BlockPos> standable) {
        Set<BlockPos> spots = new LinkedHashSet<>();
        for (BlockBox box : zone.boxes()) {
            for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
                if (standable.test(pos)) {
                    spots.add(pos.immutable());
                }
            }
        }
        List<List<BlockPos>> pieces = new ArrayList<>();
        while (!spots.isEmpty()) {
            BlockPos seed = spots.iterator().next();
            spots.remove(seed);
            List<BlockPos> piece = new ArrayList<>();
            Deque<BlockPos> queue = new ArrayDeque<>(List.of(seed));
            while (!queue.isEmpty()) {
                BlockPos at = queue.poll();
                piece.add(at);
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = at.relative(dir).above(dy);
                        if (spots.remove(next)) {
                            queue.add(next);
                        }
                    }
                }
            }
            pieces.add(piece);
        }
        pieces.sort(Comparator.comparingInt((List<BlockPos> p) -> p.size()).reversed());
        return pieces;
    }
}
