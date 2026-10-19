package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.BlockBox;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.LongConsumer;

/** A hand-built world where every cell is solid until opened. */
final class TestWorld implements CellQuery {
    static final int TOP = 6;

    private final LongOpenHashSet open = new LongOpenHashSet();
    private BlockBox bounds;

    /**
     * Rows run along z and characters along x. A '.' opens its column from y 1 to {@link #TOP} over the solid floor at
     * y 0; any other character leaves it solid. The bounds cover the plan from y 1 to {@link #TOP}.
     */
    static TestWorld floorPlan(String... rows) {
        TestWorld world = new TestWorld();
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                if (rows[z].charAt(x) == '.') {
                    world.open(x, 1, z, x, TOP, z);
                }
            }
        }
        world.bounds = new BlockBox(0, 1, 0, rows[0].length() - 1, TOP, rows.length - 1);
        return world;
    }

    static TestWorld room(int width, int depth) {
        String[] rows = new String[depth];
        Arrays.fill(rows, ".".repeat(width));
        return floorPlan(rows);
    }

    /** A square room where each column is a wall, or a floor raised 0 to 2 blocks, so random routes step and drop. */
    static TestWorld random(long seed, int size) {
        Random random = new Random(seed);
        TestWorld world = room(size, size);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int roll = random.nextInt(10);
                if (roll < 2) {
                    world.fill(x, 1, z, x, TOP, z);
                } else if (roll < 4) {
                    world.fill(x, 1, z, x, roll - 1, z);
                }
            }
        }
        return world;
    }

    TestWorld open(int x0, int y0, int z0, int x1, int y1, int z1) {
        BlockBox box = new BlockBox(x0, y0, z0, x1, y1, z1);
        forEach(box, open::add);
        return this;
    }

    TestWorld fill(int x0, int y0, int z0, int x1, int y1, int z1) {
        BlockBox box = new BlockBox(x0, y0, z0, x1, y1, z1);
        forEach(box, open::remove);
        return this;
    }

    PassabilityGrid grid(MovementClass movementClass) {
        return grid(bounds, movementClass);
    }

    /** The grid for one zone: cells outside {@code zone} read as solid, as the real caller's query does. */
    PassabilityGrid grid(BlockBox zone, MovementClass movementClass) {
        return PassabilityGrid.build(zone, (x, y, z) -> zone.contains(x, y, z) ? at(x, y, z) : Cell.SOLID, movementClass);
    }

    static List<BlockPos> walkableNodes(PassabilityGrid grid) {
        BlockBox b = grid.bounds();
        List<BlockPos> nodes = new ArrayList<>();
        for (int y = b.minY(); y <= b.maxY(); y++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    if (grid.walkable(x, y, z)) {
                        nodes.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return nodes;
    }

    @Override
    public Cell at(int x, int y, int z) {
        return open.contains(BlockPos.asLong(x, y, z)) ? Cell.OPEN : Cell.SOLID;
    }

    private static void forEach(BlockBox box, LongConsumer action) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    action.accept(BlockPos.asLong(x, y, z));
                }
            }
        }
    }
}
