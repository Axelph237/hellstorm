package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.mob.path.BlockAStar;
import dev.candycanecazoo.packapunch.mob.path.CellQuery;
import dev.candycanecazoo.packapunch.mob.path.HierarchicalPlanner;
import dev.candycanecazoo.packapunch.mob.path.MovementClass;
import dev.candycanecazoo.packapunch.mob.path.PassabilityGrid;
import dev.candycanecazoo.packapunch.mob.path.PortalPathCache;
import dev.candycanecazoo.packapunch.mob.path.ZonePathing;
import dev.candycanecazoo.packapunch.map.CellProbe;
import dev.candycanecazoo.packapunch.tools.LevelCellProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One game's zombie pathfinding. Grids are read from the world on the server thread the first time a mob of a given
 * size needs them; after that, plans run on this game's worker thread against those immutable grids.
 */
public final class GamePathing implements AutoCloseable {
    private static final int MAX_EXPANDED = 50_000;

    public record Planner(HierarchicalPlanner planner, Map<ZoneId, PassabilityGrid> grids) {}

    private final ServerLevel level;
    private final GameMap map;
    private final Passages.Result passages;
    private final PortalPathCache cache = new PortalPathCache(BlockAStar.DEFAULT, MAX_EXPANDED);
    private final Map<MovementClass, Planner> planners = new ConcurrentHashMap<>();
    private final AtomicInteger plansFollowed = new AtomicInteger();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, PackAPunch.MOD_ID + "-pathing");
        thread.setDaemon(true);
        return thread;
    });

    public GamePathing(ServerLevel level, GameMap map, Passages.Result passages) {
        this.level = level;
        this.map = map;
        this.passages = passages;
    }

    /** Call on the server thread: may read the world to build this movement class's grids. */
    public Planner planner(MovementClass mc) {
        return planners.computeIfAbsent(mc, this::build);
    }

    private Planner build(MovementClass mc) {
        CellQuery world = (x, y, z) -> LevelCellProbe.classify(level, new BlockPos(x, y, z)) == CellProbe.Cell.OPEN
                ? CellQuery.Cell.OPEN : CellQuery.Cell.SOLID;
        Map<ZoneId, PassabilityGrid> grids = ZonePathing.grids(map.map(), passages, world, mc);
        HierarchicalPlanner planner = new HierarchicalPlanner(grids, cache);
        ZonePathing.portals(passages, grids).forEach(planner::addPortal);
        return new Planner(planner, grids);
    }

    public CompletableFuture<Optional<List<BlockPos>>> plan(Planner planner, BlockPos start, ZoneId startZone, BlockPos goal, ZoneId goalZone) {
        return CompletableFuture.supplyAsync(() -> planner.planner().plan(start, startZone, goal, goalZone), worker);
    }

    /** How many cross-zone plans mobs have started following this game. */
    public int plansFollowed() {
        return plansFollowed.get();
    }

    void countFollowed() {
        plansFollowed.incrementAndGet();
    }

    public Optional<ZoneId> zoneAt(BlockPos pos) {
        return map.index().zoneAt(pos);
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
