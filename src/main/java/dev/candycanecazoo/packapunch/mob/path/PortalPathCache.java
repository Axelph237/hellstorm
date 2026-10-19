package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.ZoneId;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Memoised block paths between portal cells inside one zone, valid for a whole game because the map never changes.
 * Keys include the movement class, so mobs of different sizes never share a path. A pair that is unreachable, or that
 * exceeds {@code maxExpanded}, is remembered as empty and not retried. Safe for concurrent use: two threads racing on
 * the same new key may both search, and the first result stored wins.
 */
public final class PortalPathCache {
    private record Key(ZoneId zone, BlockPos from, BlockPos to, MovementClass movementClass) {}

    private final BlockAStar astar;
    private final int maxExpanded;
    private final ConcurrentHashMap<Key, Optional<GridPath>> edges = new ConcurrentHashMap<>();
    private final AtomicInteger searches = new AtomicInteger();

    public PortalPathCache(BlockAStar astar, int maxExpanded) {
        this.astar = astar;
        this.maxExpanded = maxExpanded;
    }

    public BlockAStar astar() {
        return astar;
    }

    public int maxExpanded() {
        return maxExpanded;
    }

    public Optional<GridPath> edge(PassabilityGrid grid, ZoneId zone, BlockPos from, BlockPos to) {
        Key key = new Key(zone, from, to, grid.movementClass());
        Optional<GridPath> cached = edges.get(key);
        if (cached != null) {
            return cached;
        }
        searches.incrementAndGet();
        Optional<GridPath> found = astar.find(grid, from, to, maxExpanded);
        Optional<GridPath> raced = edges.putIfAbsent(key, found);
        return raced == null ? found : raced;
    }

    /** How many block-level searches this cache has run. */
    public int searches() {
        return searches.get();
    }
}
