package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.BlockBox;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.Optional;

/**
 * Walkable nodes and the moves between them, as compact arrays. Built once and never changed, so any thread may read it.
 * The game builds it from vanilla's own walk evaluator ({@code VanillaGraphs}), so doors, trapdoors, panes, slabs, and
 * modded blocks behave exactly as vanilla pathfinding sees them. {@link #fieldTo} turns it into a {@link FlowField}.
 */
public final class FlowGraph {
    /** Collects nodes and moves. A move to a node that was never added is dropped. */
    public static final class Builder {
        private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
        private final LongArrayList positions = new LongArrayList();
        private final IntArrayList edgeFrom = new IntArrayList();
        private final LongArrayList edgeTo = new LongArrayList();
        private final IntArrayList edgeCost = new IntArrayList();

        private Builder() {
            index.defaultReturnValue(-1);
        }

        public Builder node(BlockPos pos) {
            long key = pos.asLong();
            if (index.get(key) < 0) {
                index.put(key, positions.size());
                positions.add(key);
            }
            return this;
        }

        public boolean has(BlockPos pos) {
            return index.get(pos.asLong()) >= 0;
        }

        public Builder move(BlockPos from, BlockPos to, int cost) {
            node(from);
            edgeFrom.add(index.get(from.asLong()));
            edgeTo.add(to.asLong());
            edgeCost.add(cost);
            return this;
        }

        public FlowGraph build() {
            int n = positions.size();
            int[] start = new int[n + 1];
            IntArrayList kept = new IntArrayList();
            for (int e = 0; e < edgeFrom.size(); e++) {
                if (index.get(edgeTo.getLong(e)) >= 0) {
                    start[edgeFrom.getInt(e) + 1]++;
                    kept.add(e);
                }
            }
            for (int i = 0; i < n; i++) {
                start[i + 1] += start[i];
            }
            int[] to = new int[kept.size()];
            int[] cost = new int[kept.size()];
            int[] fill = Arrays.copyOf(start, n);
            for (int k = 0; k < kept.size(); k++) {
                int e = kept.getInt(k);
                int slot = fill[edgeFrom.getInt(e)]++;
                to[slot] = index.get(edgeTo.getLong(e));
                cost[slot] = edgeCost.getInt(e);
            }
            Long2IntOpenHashMap frozen = new Long2IntOpenHashMap(index);
            frozen.defaultReturnValue(-1);
            return new FlowGraph(frozen, positions.toLongArray(), start, to, cost);
        }
    }

    private final Long2IntOpenHashMap index;
    private final long[] positions;
    private final int[] forwardStart;
    private final int[] forwardTo;
    private final int[] forwardCost;
    private final int[] reverseStart;
    private final int[] reverseFrom;
    private final int[] reverseCost;

    private FlowGraph(Long2IntOpenHashMap index, long[] positions, int[] forwardStart, int[] forwardTo, int[] forwardCost) {
        this.index = index;
        this.positions = positions;
        this.forwardStart = forwardStart;
        this.forwardTo = forwardTo;
        this.forwardCost = forwardCost;
        int n = positions.length;
        int[] counts = new int[n + 1];
        for (int target : forwardTo) {
            counts[target + 1]++;
        }
        for (int i = 0; i < n; i++) {
            counts[i + 1] += counts[i];
        }
        this.reverseStart = counts.clone();
        this.reverseFrom = new int[forwardTo.length];
        this.reverseCost = new int[forwardTo.length];
        int[] fill = Arrays.copyOf(counts, n);
        for (int from = 0; from < n; from++) {
            for (int e = forwardStart[from]; e < forwardStart[from + 1]; e++) {
                int slot = fill[forwardTo[e]]++;
                reverseFrom[slot] = from;
                reverseCost[slot] = forwardCost[e];
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A graph over a {@link PassabilityGrid} with {@link BlockAStar}'s moves. Used by the pure tests. */
    public static FlowGraph build(PassabilityGrid grid, BlockAStar moves) {
        Builder builder = builder();
        BlockBox b = grid.bounds();
        for (int y = b.minY(); y <= b.maxY(); y++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    if (grid.walkable(x, y, z)) {
                        BlockPos from = new BlockPos(x, y, z);
                        builder.node(from);
                        moves.forEachMove(grid, x, y, z, (tx, ty, tz, c) -> builder.move(from, new BlockPos(tx, ty, tz), c));
                    }
                }
            }
        }
        return builder.build();
    }

    public int nodeCount() {
        return positions.length;
    }

    /** The node index of a position, or -1 if it isn't a node. */
    public int indexOf(BlockPos pos) {
        return index.get(pos.asLong());
    }

    BlockPos position(int node) {
        return BlockPos.of(positions[node]);
    }

    int forwardStart(int node) {
        return forwardStart[node];
    }

    int forwardEnd(int node) {
        return forwardStart[node + 1];
    }

    int forwardTo(int edge) {
        return forwardTo[edge];
    }

    int forwardCost(int edge) {
        return forwardCost[edge];
    }

    /** Every node's walking cost to {@code goal}, by a Dijkstra over reversed moves (drops are one-way). Empty if the goal isn't a node. */
    public Optional<FlowField> fieldTo(BlockPos goal) {
        int target = indexOf(goal);
        if (target < 0) {
            return Optional.empty();
        }
        int[] distance = new int[positions.length];
        Arrays.fill(distance, FlowField.UNREACHABLE);
        distance[target] = 0;
        LongHeapPriorityQueue queue = new LongHeapPriorityQueue();
        queue.enqueue(target);
        while (!queue.isEmpty()) {
            long entry = queue.dequeueLong();
            int node = (int) entry;
            int d = (int) (entry >>> 32);
            if (d > distance[node]) {
                continue;
            }
            for (int e = reverseStart[node]; e < reverseStart[node + 1]; e++) {
                int from = reverseFrom[e];
                int next = d + reverseCost[e];
                if (next < distance[from]) {
                    distance[from] = next;
                    queue.enqueue(((long) next << 32) | from);
                }
            }
        }
        return Optional.of(new FlowField(this, goal.immutable(), distance));
    }
}
