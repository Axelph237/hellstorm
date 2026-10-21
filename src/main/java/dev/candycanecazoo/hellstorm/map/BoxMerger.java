package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a set of block cells into a list of non-overlapping boxes that cover exactly those cells.
 * Greedy: from the lowest unclaimed cell, grow along x, then z, then y while every cell in the new slice is present and unclaimed.
 */
public final class BoxMerger {
    private BoxMerger() {}

    public static List<BlockBox> merge(Set<BlockPos> cells) {
        List<BlockPos> ordered = new ArrayList<>(cells);
        ordered.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        Set<BlockPos> claimed = new HashSet<>();
        List<BlockBox> boxes = new ArrayList<>();
        for (BlockPos origin : ordered) {
            if (claimed.contains(origin)) {
                continue;
            }
            int x0 = origin.getX(), y0 = origin.getY(), z0 = origin.getZ();
            int x1 = x0;
            while (free(cells, claimed, x1 + 1, y0, z0)) {
                x1++;
            }
            int z1 = z0;
            while (rowFree(cells, claimed, x0, x1, y0, z1 + 1)) {
                z1++;
            }
            int y1 = y0;
            while (layerFree(cells, claimed, x0, x1, y1 + 1, z0, z1)) {
                y1++;
            }
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        claimed.add(new BlockPos(x, y, z));
                    }
                }
            }
            boxes.add(new BlockBox(x0, y0, z0, x1, y1, z1));
        }
        return boxes;
    }

    private static boolean free(Set<BlockPos> cells, Set<BlockPos> claimed, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return cells.contains(pos) && !claimed.contains(pos);
    }

    private static boolean rowFree(Set<BlockPos> cells, Set<BlockPos> claimed, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) {
            if (!free(cells, claimed, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static boolean layerFree(Set<BlockPos> cells, Set<BlockPos> claimed, int x0, int x1, int y, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            if (!rowFree(cells, claimed, x0, x1, y, z)) {
                return false;
            }
        }
        return true;
    }
}
