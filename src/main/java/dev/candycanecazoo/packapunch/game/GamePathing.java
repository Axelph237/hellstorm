package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.map.CellProbe;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.mob.path.BlockAStar;
import dev.candycanecazoo.packapunch.mob.path.CellQuery;
import dev.candycanecazoo.packapunch.mob.path.FlowField;
import dev.candycanecazoo.packapunch.mob.path.FlowGraph;
import dev.candycanecazoo.packapunch.mob.path.MovementClass;
import dev.candycanecazoo.packapunch.mob.path.Portal;
import dev.candycanecazoo.packapunch.mob.path.ZonePathing;
import dev.candycanecazoo.packapunch.tools.LevelCellProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One game's zombie pathfinding: a {@link FlowGraph} per movement class over every unlocked zone, and a {@link FlowField}
 * per chased player. Graphs are read from the world on the server thread the first time a mob of that size needs one.
 * Fields are recomputed on this game's worker thread when their player moves to another cell; zombies always read the
 * latest finished field and never wait.
 */
public final class GamePathing implements AutoCloseable {
    private static final int RECOMPUTE_TICKS = 5;

    private record FieldKey(MovementClass mc, UUID target) {}

    private static final class FieldState {
        volatile FlowField field;
        BlockPos requestedGoal;
        boolean requested;
        long requestedAt;
        boolean running;
    }

    private final ServerLevel level;
    private final GameMap map;
    private final Passages.Result passages;
    private final Map<MovementClass, FlowGraph> graphs = new ConcurrentHashMap<>();
    private final Map<FieldKey, FieldState> fields = new ConcurrentHashMap<>();
    private final AtomicInteger flowSteps = new AtomicInteger();
    private volatile List<Portal> doorways;
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

    private CellQuery world() {
        return (x, y, z) -> LevelCellProbe.classify(level, new BlockPos(x, y, z)) == CellProbe.Cell.OPEN ? CellQuery.Cell.OPEN : CellQuery.Cell.SOLID;
    }

    /** Call on the server thread: may read the world to build this movement class's graph. */
    public FlowGraph graph(MovementClass mc) {
        return graphs.computeIfAbsent(mc, size -> FlowGraph.build(
                ZonePathing.unionGrid(map.map(), map.unlocked(), passages, world(), size), BlockAStar.DEFAULT));
    }

    /**
     * The latest field toward {@code target} for this movement class, starting a recompute if the target has moved to a
     * new cell. Empty until the first field is ready, or if the target isn't standing on the grid.
     */
    public Optional<FlowField> field(MovementClass mc, Entity target, BlockPos targetCell) {
        FlowGraph graph = graph(mc);
        FieldState state = fields.computeIfAbsent(new FieldKey(mc, target.getUUID()), k -> new FieldState());
        long now = level.getGameTime();
        synchronized (state) {
            boolean moved = !targetCell.equals(state.requestedGoal);
            boolean due = !state.requested || now - state.requestedAt >= RECOMPUTE_TICKS;
            if (moved && due && !state.running && graph.indexOf(targetCell) >= 0) {
                state.running = true;
                state.requested = true;
                state.requestedGoal = targetCell.immutable();
                state.requestedAt = now;
                BlockPos goal = state.requestedGoal;
                worker.execute(() -> {
                    try {
                        Optional<FlowField> computed = graph.fieldTo(goal);
                        computed.ifPresent(f -> state.field = f);
                    } catch (RuntimeException e) {
                        PackAPunch.LOG.error("Flow field toward {} failed", goal, e);
                    } finally {
                        synchronized (state) {
                            state.running = false;
                        }
                    }
                });
            }
        }
        return Optional.ofNullable(state.field);
    }

    /** The doorway portals a normal zombie uses, for the {@code show_paths} view. */
    public List<Portal> doorways(MovementClass mc) {
        if (doorways == null) {
            doorways = List.copyOf(ZonePathing.portals(passages, ZonePathing.grids(map.map(), passages, world(), mc)));
        }
        return doorways;
    }

    public Optional<dev.candycanecazoo.packapunch.map.ZoneId> zoneAt(BlockPos pos) {
        return map.index().zoneAt(pos);
    }

    /** How many steering decisions came from a flow field this game (tests use it to prove the field drives zombies). */
    public int flowSteps() {
        return flowSteps.get();
    }

    void countFlowStep() {
        flowSteps.incrementAndGet();
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
