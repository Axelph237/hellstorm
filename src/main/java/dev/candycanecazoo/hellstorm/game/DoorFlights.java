package dev.candycanecazoo.hellstorm.game;

import com.mojang.math.Transformation;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A bought door's blocks float up out of the doorway and shrink away, the top first and each layer from the middle out.
 * The real blocks are gone the moment the door is bought, so the doorway is open while the copies rise. Each copy is a
 * vanilla block display: it's sent once at rest and once with where to go, and the client moves it smoothly from there,
 * so a door costs two small packets per block and nothing per frame.
 *
 * <p>The copies are never kept. A map reset discards them, and one saved with its chunk (the chunk unloaded mid-flight,
 * or the server died) is refused when the chunk loads again.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class DoorFlights {
    public static final String TAG = Hellstorm.MOD_ID + "_door_flight";
    /**
     * Ticks between spawning the copies and sending them off. A client that gets a display's start and end in the same
     * tick has nothing to move from and draws only the end.
     */
    public static final int LAUNCH_TICKS = 2;
    public static final int RISE_TICKS = 16;
    private static final float RISE_BLOCKS = 1.5f;
    /** Ticks from one step of the wave to the next, shortened for big doors so the wave never takes longer than {@link #MAX_STAGGER_TICKS}. */
    static final int STEP_TICKS = 3;
    public static final int MAX_STAGGER_TICKS = 24;
    /** A block is removed this long after its client should have shrunk it to nothing, so a client running behind still sees it finish. */
    private static final int LINGER_TICKS = 3;
    /** The longest any door's flight lasts, from purchase to its last copy being removed. */
    public static final int MAX_TICKS = LAUNCH_TICKS + MAX_STAGGER_TICKS + RISE_TICKS + LINGER_TICKS;

    private static final List<Flight> FLIGHTS = new ArrayList<>();

    private record Lift(Display.BlockDisplay display, int delay) {}

    private static final class Flight {
        final ServerLevel level;
        final Vec3 center;
        final List<Lift> lifts;
        int age;

        Flight(ServerLevel level, Vec3 center, List<Lift> lifts) {
            this.level = level;
            this.center = center;
            this.lifts = lifts;
        }

        /** Returns whether the flight is over. */
        boolean tick() {
            age++;
            if (age == LAUNCH_TICKS) {
                lifts.forEach(DoorFlights::launch);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_EYE_LAUNCH, SoundSource.BLOCKS, 0.8f, 0.9f);
            }
            lifts.removeIf(lift -> {
                if (age < LAUNCH_TICKS + lift.delay() + RISE_TICKS + LINGER_TICKS) {
                    return false;
                }
                lift.display().discard();
                return true;
            });
            return lifts.isEmpty();
        }
    }

    private DoorFlights() {}

    /** Sends a copy of each block up out of the doorway. The blocks themselves have already been removed. */
    static void start(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        Map<BlockPos, BlockState> solid = new HashMap<>(blocks);
        solid.values().removeIf(BlockState::isAir);
        if (solid.isEmpty()) {
            return;
        }
        Map<BlockPos, Integer> delays = stagger(solid.keySet());
        List<Lift> lifts = new ArrayList<>();
        Vec3 sum = Vec3.ZERO;
        for (Map.Entry<BlockPos, BlockState> block : solid.entrySet()) {
            BlockPos pos = block.getKey();
            Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
            if (display == null) {
                continue;
            }
            display.setBlockState(block.getValue());
            // Standing on the block's bottom centre, the display's culling box covers the whole climb.
            display.setWidth(1.0f);
            display.setHeight(1.0f + RISE_BLOCKS);
            display.setTransformation(pose(0));
            display.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            display.addTag(TAG);
            level.addFreshEntity(display);
            lifts.add(new Lift(display, delays.get(pos)));
            sum = sum.add(pos.getCenter());
        }
        FLIGHTS.add(new Flight(level, sum.scale(1.0 / solid.size()), lifts));
    }

    /** The client moves the copy from where it sits now to the end over {@link #RISE_TICKS}, once its delay has passed. */
    private static void launch(Lift lift) {
        Display.BlockDisplay display = lift.display();
        if (display.isRemoved()) {
            return;
        }
        display.setInterpolationDelay(lift.delay());
        display.setInterpolationDuration(RISE_TICKS);
        display.setTransformation(pose(1));
    }

    /**
     * The block at a point in its flight, from 0 (in place) to 1 (risen and shrunk to nothing). It shrinks about its own
     * centre, so blocks side by side never touch.
     */
    private static Transformation pose(float progress) {
        float scale = 1 - progress;
        float half = scale / 2;
        return new Transformation(new Vector3f(-half, 0.5f + RISE_BLOCKS * progress - half, -half), null, new Vector3f(scale), null);
    }

    /**
     * Each block's delay in ticks. The top layer goes first and lower layers follow, each spreading from the door's middle
     * out to its edges at half the speed, so a rising block never passes through one still waiting above it. A step is
     * {@link #STEP_TICKS}, shortened so the whole wave fits in {@link #MAX_STAGGER_TICKS}.
     */
    static Map<BlockPos, Integer> stagger(Collection<BlockPos> blocks) {
        int top = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            top = Math.max(top, pos.getY());
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        double middleX = (minX + maxX) / 2.0;
        double middleZ = (minZ + maxZ) / 2.0;
        Map<BlockPos, Double> steps = new HashMap<>();
        double last = 0;
        for (BlockPos pos : blocks) {
            double step = (top - pos.getY()) + Math.hypot(pos.getX() - middleX, pos.getZ() - middleZ) / 2;
            steps.put(pos, step);
            last = Math.max(last, step);
        }
        double tick = last * STEP_TICKS > MAX_STAGGER_TICKS ? MAX_STAGGER_TICKS / last : STEP_TICKS;
        Map<BlockPos, Integer> delays = new HashMap<>();
        steps.forEach((pos, step) -> delays.put(pos, (int) Math.round(step * tick)));
        return delays;
    }

    /** Removes every copy in the level at once, wherever it is in its flight. */
    static void clear(ServerLevel level) {
        FLIGHTS.removeIf(flight -> flight.level == level);
        level.getEntities(EntityType.BLOCK_DISPLAY, DoorFlights::isCopy).forEach(Entity::discard);
    }

    public static boolean isCopy(Entity entity) {
        return entity.getTags().contains(TAG);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FLIGHTS.removeIf(Flight::tick);
        }
    }

    /** A copy loaded from disk was saved mid-flight, and its flight is over or forgotten: it never comes back. */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && !event.getLevel().isClientSide() && isCopy(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
