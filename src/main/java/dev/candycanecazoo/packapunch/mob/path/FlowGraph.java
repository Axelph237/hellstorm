package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.BlockBox;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import net.minecraft.core.BlockPos;

import java.util.Arrays;

/**
 * Every walkable node of one grid with its moves, as compact arrays. Built once per movement class and never changed, so
 * any thread may read it. {@link #fieldTo} turns it into a {@link FlowField} toward one goal.
 */
public final class FlowGraph {
    private final PassabilityGrid grid;
    private final int[] indexInBounds;
    private final long[] positions;
    private final int[] forwardStart;
    private final int[] forwardTo;
    private final int[] forwardCost;
    private final int[] reverseStart;
    private final int[] reverseFrom;
    private final int[] reverseCost;

    private FlowGraph(PassabilityGrid grid, int[] indexInBounds, long[] positions, int[] forwardStart, int[] forwardTo, int[] forwardCost) {
        this.grid = grid;
        this.indexInBounds = indexInBounds;
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

    public static FlowGraph build(PassabilityGrid grid, BlockAStar moves) {
        BlockBox b = grid.bounds();
        int[] index = new int[Math.toIntExact(b.volume())];
        Arrays.fill(index, -1);
        IntArrayList walkable = new IntArrayList();
        for (int y = b.minY(); y <= b.maxY(); y++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    if (grid.walkable(x, y, z)) {
                        int local = local(b, x, y, z);
                        index[local] = walkable.size();
                        walkable.add(local);
                    }
                }
            }
        }
        long[] positions = new long[walkable.size()];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = global(b, walkable.getInt(i)).asLong();
        }
        int[] start = new int[positions.length + 1];
        IntArrayList to = new IntArrayList();
        IntArrayList cost = new IntArrayList();
        for (int i = 0; i < positions.length; i++) {
            long p = positions[i];
            moves.forEachMove(grid, BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p), (x, y, z, c) -> {
                to.add(index[local(b, x, y, z)]);
                cost.add(c);
            });
            start[i + 1] = to.size();
        }
        return new FlowGraph(grid, index, positions, start, to.toIntArray(), cost.toIntArray());
    }

    public PassabilityGrid grid() {
        return grid;
    }

    public int nodeCount() {
        return positions.length;
    }

    /** The node index of a walkable cell, or -1. */
    public int indexOf(BlockPos pos) {
        return grid.walkable(pos) ? indexInBounds[local(grid.bounds(), pos.getX(), pos.getY(), pos.getZ())] : -1;
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

    /** Every node's walking cost to {@code goal}, by a Dijkstra over reversed moves (drops are one-way). Empty if the goal isn't walkable. */
    public java.util.Optional<FlowField> fieldTo(BlockPos goal) {
        int target = indexOf(goal);
        if (target < 0) {
            return java.util.Optional.empty();
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
        return java.util.Optional.of(new FlowField(this, goal.immutable(), distance));
    }

    private static int local(BlockBox b, int x, int y, int z) {
        return ((y - b.minY()) * b.sizeZ() + (z - b.minZ())) * b.sizeX() + (x - b.minX());
    }

    private static BlockPos global(BlockBox b, int local) {
        int x = local % b.sizeX();
        int z = (local / b.sizeX()) % b.sizeZ();
        int y = local / (b.sizeX() * b.sizeZ());
        return new BlockPos(b.minX() + x, b.minY() + y, b.minZ() + z);
    }
}
