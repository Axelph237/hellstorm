package dev.candycanecazoo.hellstorm.mob.path;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.PriorityQueue;

/** Reference shortest paths over the same move set as a {@link BlockAStar}, with no heuristic and no budget. */
final class Dijkstra {
    private record Entry(int cost, long pos) {}

    private Dijkstra() {}

    /** The true cost from {@code start} to every reachable node. */
    static Long2IntOpenHashMap costsFrom(BlockAStar astar, PassabilityGrid grid, BlockPos start) {
        Long2IntOpenHashMap settled = new Long2IntOpenHashMap();
        if (!grid.walkable(start)) {
            return settled;
        }
        PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingInt(Entry::cost));
        queue.add(new Entry(0, start.asLong()));
        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            if (settled.containsKey(entry.pos())) {
                continue;
            }
            settled.put(entry.pos(), entry.cost());
            astar.forEachMove(grid, BlockPos.getX(entry.pos()), BlockPos.getY(entry.pos()), BlockPos.getZ(entry.pos()),
                    (x, y, z, cost) -> queue.add(new Entry(entry.cost() + cost, BlockPos.asLong(x, y, z))));
        }
        return settled;
    }
}
