package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.mob.path.FlowField;
import dev.candycanecazoo.hellstorm.mob.path.FlowGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One game's guide for zombies far from their target. For each mob type it keeps a {@link FlowGraph} of every cell vanilla
 * navigation can walk ({@link VanillaGraphs}), and for each chased player a {@link FlowField}: the walking distance from
 * every cell to that player. A zombie near its target walks straight at it on vanilla navigation; a far one is handed a
 * point some way down the field, which vanilla navigation walks to. Rooms, doorways, and passages play no part: a door
 * matters only because its blocks are a wall until it's bought.
 * Graphs are built on the server thread the first time a mob of that type needs one. Fields are recomputed on this game's
 * worker thread when their player moves to another cell; zombies always read the latest finished field and never wait.
 */
public final class GamePathing implements AutoCloseable {
    /** Within this walk of its target (in tenths of a block) a zombie goes straight for it: vanilla finds that easily. */
    static final int NEAR = 200;
    /** How far down the field a far zombie's next point is. */
    static final int LEG = 160;
    private static final int RECOMPUTE_TICKS = 5;

    private record FieldKey(EntityType<?> type, UUID target) {}

    private static final class FieldState {
        volatile FlowField field;
        BlockPos requestedGoal;
        boolean requested;
        long requestedAt;
        boolean running;
    }

    private final ServerLevel level;
    private final GameMap map;
    private volatile Set<ZoneId> unlocked;
    private volatile Set<BlockPos> shut;
    private final Map<EntityType<?>, FlowGraph> graphs = new ConcurrentHashMap<>();
    private final Map<FieldKey, FieldState> fields = new ConcurrentHashMap<>();
    private final AtomicInteger guidedLegs = new AtomicInteger();
    private final AtomicInteger stalls = new AtomicInteger();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, Hellstorm.MOD_ID + "-pathing");
        thread.setDaemon(true);
        return thread;
    });

    public GamePathing(ServerLevel level, GameMap map) {
        this.level = level;
        this.map = map;
        this.unlocked = map.unlocked();
        this.shut = shutBlocks(map, Set.of());
    }

    /**
     * A door was bought: its blocks are gone and more of the map is in play. Graphs and fields are rebuilt as zombies next
     * ask for them; zombies keep their old guide until the new one is ready.
     */
    public void doorOpened(Set<ZoneId> unlocked, Set<String> openDoors) {
        this.unlocked = Set.copyOf(unlocked);
        this.shut = shutBlocks(map, openDoors);
        graphs.clear();
        fields.clear();
    }

    private static Set<BlockPos> shutBlocks(GameMap map, Set<String> openDoors) {
        Set<BlockPos> blocks = new HashSet<>();
        for (DoorDef door : map.map().doors().values()) {
            if (!openDoors.contains(door.id())) {
                blocks.addAll(door.members());
            }
        }
        return Set.copyOf(blocks);
    }

    /** True for the blocks of doors nobody has bought yet. A wooden door or a cobweb there is still a wall. */
    boolean shut(BlockPos pos) {
        return shut.contains(pos);
    }

    /** True for mobs that walk with vanilla's walk evaluator, the only kind a flow graph describes. */
    public static boolean walks(Mob mob) {
        return mob.getNavigation().getNodeEvaluator() instanceof WalkNodeEvaluator;
    }

    /** Call on the server thread: the first call for a mob type reads the world, using {@code mob} as the template. */
    public FlowGraph graph(Mob mob) {
        return graphs.computeIfAbsent(mob.getType(), type -> VanillaGraphs.build(level, mob, map, unlocked, this::shut));
    }

    /**
     * Where a zombie far from {@code target} should walk next: a cell about {@link #LEG} down the target's field. Empty when
     * it's within {@link #NEAR} of the target, off the graph, or before the target's first field is ready; it then goes
     * straight for the target.
     */
    public Optional<BlockPos> waypoint(Mob mob, Entity target, int tieBreak) {
        FlowGraph graph = graph(mob);
        Optional<BlockPos> from = snap(graph, mob.blockPosition());
        Optional<BlockPos> goal = snap(graph, target.blockPosition());
        if (from.isEmpty() || goal.isEmpty()) {
            return Optional.empty();
        }
        return field(mob, target, goal.get()).flatMap(field -> {
            int distance = field.distance(from.get());
            return distance == FlowField.UNREACHABLE || distance <= NEAR ? Optional.empty() : field.ahead(from.get(), LEG, tieBreak);
        });
    }

    /**
     * The latest field toward {@code target} for this mob's type, starting a recompute if the target has moved to a new
     * cell. Empty until the first field is ready, or if the target isn't standing on the graph.
     */
    private Optional<FlowField> field(Mob mob, Entity target, BlockPos targetCell) {
        FlowGraph graph = graph(mob);
        FieldState state = fields.computeIfAbsent(new FieldKey(mob.getType(), target.getUUID()), k -> new FieldState());
        long now = level.getGameTime();
        synchronized (state) {
            boolean moved = !targetCell.equals(state.requestedGoal);
            boolean due = !state.requested || now - state.requestedAt >= RECOMPUTE_TICKS;
            // A zombie left over from a game that has ended may still ask: its worker is gone, so nothing is computed.
            if (moved && due && !state.running && !worker.isShutdown() && graph.indexOf(targetCell) >= 0) {
                state.running = true;
                state.requested = true;
                state.requestedGoal = targetCell.immutable();
                state.requestedAt = now;
                BlockPos goal = state.requestedGoal;
                worker.execute(() -> {
                    try {
                        graph.fieldTo(goal).ifPresent(f -> state.field = f);
                    } catch (RuntimeException e) {
                        Hellstorm.LOG.error("Flow field toward {} failed", goal, e);
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

    /** Standing on a slab or mid-jump puts a mob's block position off the graph's nodes, so look one block around. */
    private static Optional<BlockPos> snap(FlowGraph graph, BlockPos pos) {
        for (int dy : new int[] {0, -1, 1}) {
            if (graph.indexOf(pos.above(dy)) >= 0) {
                return Optional.of(pos.above(dy));
            }
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (graph.indexOf(pos.relative(d)) >= 0) {
                return Optional.of(pos.relative(d));
            }
        }
        return Optional.empty();
    }

    /** How many times a far zombie was handed a point down a field this game (tests use it to prove the guide is used). */
    public int guidedLegs() {
        return guidedLegs.get();
    }

    void countGuidedLeg() {
        guidedLegs.incrementAndGet();
    }

    /** How many times a chasing zombie went two seconds without getting half a block anywhere. */
    public int stalls() {
        return stalls.get();
    }

    void countStall() {
        stalls.incrementAndGet();
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
