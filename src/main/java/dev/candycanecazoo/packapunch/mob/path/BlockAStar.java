package dev.candycanecazoo.packapunch.mob.path;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * A* over a {@link PassabilityGrid}. Moves are the four horizontal neighbours on the same layer, a step up of exactly
 * one block, and a drop of 1 to {@code maxDrop} blocks. Stateless, so one instance can serve many threads.
 */
public final class BlockAStar {
    public static final int DEFAULT_MAX_DROP = 3;
    public static final BlockAStar DEFAULT = new BlockAStar(Mode.MANHATTAN, DEFAULT_MAX_DROP);

    public enum Mode {
        /** Axis-aligned moves costing 1 per block. Manhattan is admissible and consistent because every move costs exactly its own Manhattan length. */
        MANHATTAN(1, false) {
            @Override
            int estimate(int dx, int dy, int dz) {
                return dx + dy + dz;
            }
        },
        /**
         * Adds the four horizontal diagonals, without cutting corners. Costs 10 per straight or vertical block and 14
         * per diagonal. Octile is admissible and consistent because every move costs at least its own octile length.
         */
        OCTILE(10, true) {
            @Override
            int estimate(int dx, int dy, int dz) {
                return 10 * Math.max(dx, dz) + 4 * Math.min(dx, dz) + 10 * dy;
            }
        };

        private final int costPerBlock;
        private final boolean diagonals;

        Mode(int costPerBlock, boolean diagonals) {
            this.costPerBlock = costPerBlock;
            this.diagonals = diagonals;
        }

        /** The cost of one straight block, for converting path costs back to blocks. */
        public int costPerBlock() {
            return costPerBlock;
        }

        /** The heuristic for absolute axis distances. */
        abstract int estimate(int dx, int dy, int dz);
    }

    @FunctionalInterface
    interface MoveSink {
        void accept(int x, int y, int z, int cost);
    }

    private static final int DIAGONAL_COST = 14;
    private static final int[][] STRAIGHT = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** Ties on f go to the node nearer the goal, then to the lower packed position, so results never depend on hash order. */
    private record Open(int f, int h, long pos) {}

    private static final Comparator<Open> ORDER = Comparator.comparingInt(Open::f).thenComparingInt(Open::h).thenComparingLong(Open::pos);

    private final Mode mode;
    private final int maxDrop;

    public BlockAStar(Mode mode, int maxDrop) {
        if (maxDrop < 0) {
            throw new IllegalArgumentException("maxDrop must be >= 0, got " + maxDrop);
        }
        this.mode = mode;
        this.maxDrop = maxDrop;
    }

    public Mode mode() {
        return mode;
    }

    public int heuristic(BlockPos a, BlockPos b) {
        return mode.estimate(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ()));
    }

    /** The cheapest path, or empty when the goal is unreachable or the search would expand more than {@code maxExpanded} nodes. */
    public Optional<GridPath> find(PassabilityGrid grid, BlockPos start, BlockPos goal, int maxExpanded) {
        if (!grid.walkable(start) || !grid.walkable(goal)) {
            return Optional.empty();
        }
        long startKey = start.asLong();
        long goalKey = goal.asLong();
        int gx = goal.getX();
        int gy = goal.getY();
        int gz = goal.getZ();
        Long2IntOpenHashMap cost = new Long2IntOpenHashMap();
        cost.defaultReturnValue(Integer.MAX_VALUE);
        Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        LongOpenHashSet closed = new LongOpenHashSet();
        PriorityQueue<Open> open = new PriorityQueue<>(ORDER);
        cost.put(startKey, 0);
        int startH = heuristic(start, goal);
        open.add(new Open(startH, startH, startKey));
        int expanded = 0;
        while (!open.isEmpty()) {
            long current = open.poll().pos();
            if (!closed.add(current)) {
                continue;
            }
            if (current == goalKey) {
                return Optional.of(new GridPath(trace(parent, startKey, goalKey), cost.get(goalKey), expanded));
            }
            if (expanded == maxExpanded) {
                return Optional.empty();
            }
            expanded++;
            int base = cost.get(current);
            forEachMove(grid, BlockPos.getX(current), BlockPos.getY(current), BlockPos.getZ(current), (x, y, z, step) -> {
                long next = BlockPos.asLong(x, y, z);
                int g = base + step;
                if (g < cost.get(next)) {
                    cost.put(next, g);
                    parent.put(next, current);
                    int h = mode.estimate(Math.abs(x - gx), Math.abs(y - gy), Math.abs(z - gz));
                    open.add(new Open(g + h, h, next));
                }
            });
        }
        return Optional.empty();
    }

    void forEachMove(PassabilityGrid grid, int x, int y, int z, MoveSink sink) {
        int height = grid.movementClass().height();
        int block = mode.costPerBlock;
        for (int[] d : STRAIGHT) {
            int tx = x + d[0];
            int tz = z + d[1];
            if (grid.walkable(tx, y, tz)) {
                sink.accept(tx, y, tz, block);
            } else if (grid.walkable(tx, y + 1, tz) && grid.clear(x, y + height, z, 1)) {
                sink.accept(tx, y + 1, tz, 2 * block);
            } else if (grid.clear(tx, y, tz, height)) {
                for (int drop = 1; drop <= maxDrop && grid.clear(tx, y - drop, tz, 1); drop++) {
                    if (grid.walkable(tx, y - drop, tz)) {
                        sink.accept(tx, y - drop, tz, (1 + drop) * block);
                        break;
                    }
                }
            }
        }
        if (mode.diagonals) {
            for (int[] d : DIAGONAL) {
                int tx = x + d[0];
                int tz = z + d[1];
                if (grid.walkable(tx, y, tz) && grid.walkable(tx, y, z) && grid.walkable(x, y, tz)) {
                    sink.accept(tx, y, tz, DIAGONAL_COST);
                }
            }
        }
    }

    private static List<BlockPos> trace(Long2LongOpenHashMap parent, long startKey, long goalKey) {
        List<BlockPos> nodes = new ArrayList<>();
        for (long at = goalKey; at != startKey; at = parent.get(at)) {
            nodes.add(BlockPos.of(at));
        }
        nodes.add(BlockPos.of(startKey));
        Collections.reverse(nodes);
        return nodes;
    }
}
