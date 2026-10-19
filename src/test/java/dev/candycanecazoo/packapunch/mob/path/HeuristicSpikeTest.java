package dev.candycanecazoo.packapunch.mob.path;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 spike: axis-aligned moves with Manhattan against diagonal moves with octile. Each mode must find an optimal
 * path for its own move set; the printed table compares path cost and nodes expanded.
 */
class HeuristicSpikeTest {
    private static final MovementClass ZOMBIE = new MovementClass(1, 2);

    private record Layout(String name, TestWorld world, BlockPos start, BlockPos goal) {}

    private static Layout openRoom() {
        return new Layout("open room", TestWorld.room(24, 24), new BlockPos(0, 1, 0), new BlockPos(23, 1, 23));
    }

    /** Nine rows, with the wall rows between them open at alternating ends. */
    private static Layout corridor() {
        String[] rows = new String[9];
        for (int z = 0; z < rows.length; z++) {
            if (z % 2 == 0) {
                rows[z] = ".".repeat(20);
            } else {
                rows[z] = z % 4 == 1 ? "#".repeat(19) + "." : "." + "#".repeat(19);
            }
        }
        return new Layout("corridor", TestWorld.floorPlan(rows), new BlockPos(0, 1, 0), new BlockPos(19, 1, 8));
    }

    private static Layout pillars() {
        TestWorld world = TestWorld.room(24, 24);
        for (int x = 2; x < 20; x += 5) {
            for (int z = 2; z < 20; z += 5) {
                world.fill(x, 1, z, x + 1, TestWorld.TOP, z + 1);
            }
        }
        return new Layout("pillars", world, new BlockPos(0, 1, 0), new BlockPos(23, 1, 23));
    }

    /** A 16x16 room whose east half is a mezzanine three blocks up, reached by a stair in the north-west corner. */
    private static Layout twoLevels() {
        TestWorld world = TestWorld.room(16, 16)
                .fill(8, 1, 0, 15, 3, 15)
                .fill(5, 1, 0, 5, 1, 1)
                .fill(6, 1, 0, 6, 2, 1)
                .fill(7, 1, 0, 7, 3, 1);
        return new Layout("two levels", world, new BlockPos(0, 1, 15), new BlockPos(15, 4, 15));
    }

    @Test
    void bothModesFindOptimalPathsForTheirMoveSets() {
        List<String> table = new ArrayList<>();
        table.add(String.format("%-12s %-10s %14s %9s %7s", "layout", "mode", "cost (blocks)", "expanded", "nodes"));
        for (Layout layout : List.of(openRoom(), corridor(), pillars(), twoLevels())) {
            PassabilityGrid grid = layout.world().grid(ZOMBIE);
            for (BlockAStar.Mode mode : BlockAStar.Mode.values()) {
                BlockAStar astar = new BlockAStar(mode, BlockAStar.DEFAULT_MAX_DROP);
                GridPath path = astar.find(grid, layout.start(), layout.goal(), 1_000_000).orElseThrow();
                int optimal = Dijkstra.costsFrom(astar, grid, layout.start()).get(layout.goal().asLong());
                assertEquals(optimal, path.cost(), layout.name() + " " + mode);
                table.add(String.format("%-12s %-10s %14.1f %9d %7d", layout.name(), mode,
                        (double) path.cost() / mode.costPerBlock(), path.expanded(), path.nodes().size()));
            }
        }
        System.out.println(String.join("\n", table));
    }
}
