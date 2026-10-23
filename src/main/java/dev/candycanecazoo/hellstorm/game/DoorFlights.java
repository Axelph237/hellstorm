package dev.candycanecazoo.hellstorm.game;

import com.mojang.math.Transformation;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A bought door's blocks rise together, shrinking a little, then burst: they swell past full size on the impact sound's peak
 * ({@link GameSounds#IMPACT_PEAK_TICKS}) and shrink fast to nothing. The real blocks are gone the moment the door is bought,
 * so the doorway is open while the copies move. Each copy is a vanilla block display: it's sent at rest and then once per
 * stage with where to go, and the client moves it smoothly from there, so a block costs a few small packets and nothing
 * per frame.
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
    /** The copies rise this far, to this size, then swell to {@link #BURST_SCALE} over {@link #SWELL_TICKS} and shrink away over {@link #VANISH_TICKS}. */
    private static final float RISE_BLOCKS = 1.0f;
    private static final float RISEN_SCALE = 0.85f;
    /** The rise ends this long before the peak, so the copies hang a moment before they burst. */
    private static final int HANG_TICKS = 3;
    private static final float BURST_SCALE = 1.25f;
    private static final int SWELL_TICKS = 2;
    private static final int VANISH_TICKS = 3;
    /** A copy is removed this long after its client should have shrunk it to nothing, so a client running behind still sees it finish. */
    private static final int LINGER_TICKS = 3;
    /** How long a flight lasts, from purchase to its copies being removed. */
    public static final int MAX_TICKS = GameSounds.IMPACT_PEAK_TICKS + SWELL_TICKS + VANISH_TICKS + LINGER_TICKS;

    /** At {@code tick} of the flight, every copy starts moving to {@code pose} over {@code ticks}. */
    private record Stage(int tick, Transformation pose, int ticks) {}

    private static final List<Stage> STAGES = List.of(
            new Stage(LAUNCH_TICKS, pose(RISE_BLOCKS, RISEN_SCALE), GameSounds.IMPACT_PEAK_TICKS - LAUNCH_TICKS - HANG_TICKS),
            new Stage(GameSounds.IMPACT_PEAK_TICKS, pose(RISE_BLOCKS, BURST_SCALE), SWELL_TICKS),
            new Stage(GameSounds.IMPACT_PEAK_TICKS + SWELL_TICKS, pose(RISE_BLOCKS, 0), VANISH_TICKS));

    private static final List<Flight> FLIGHTS = new ArrayList<>();

    private static final class Flight {
        final List<Display.BlockDisplay> copies;
        int age;

        Flight(List<Display.BlockDisplay> copies) {
            this.copies = copies;
        }

        /** Returns whether the flight is over. */
        boolean tick() {
            age++;
            for (Stage stage : STAGES) {
                if (age == stage.tick()) {
                    copies.stream().filter(copy -> !copy.isRemoved()).forEach(copy -> move(copy, stage));
                }
            }
            if (age < MAX_TICKS) {
                return false;
            }
            copies.forEach(Entity::discard);
            return true;
        }
    }

    private DoorFlights() {}

    /** Sends a copy of each block up out of the doorway to burst. The blocks themselves have already been removed. */
    static void start(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        List<Display.BlockDisplay> copies = new ArrayList<>();
        blocks.forEach((pos, state) -> {
            Display.BlockDisplay display = state.isAir() ? null : EntityType.BLOCK_DISPLAY.create(level);
            if (display == null) {
                return;
            }
            display.setBlockState(state);
            // Standing on the block's bottom centre, the display's culling box covers the climb and the swell.
            display.setWidth(BURST_SCALE);
            display.setHeight(1.0f + RISE_BLOCKS + BURST_SCALE / 2);
            display.setTransformation(pose(0, 1));
            display.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            display.addTag(TAG);
            level.addFreshEntity(display);
            copies.add(display);
        });
        if (!copies.isEmpty()) {
            FLIGHTS.add(new Flight(copies));
        }
    }

    /** The client moves the copy from where it sits now to the stage's pose over its ticks. */
    private static void move(Display.BlockDisplay copy, Stage stage) {
        copy.setInterpolationDelay(0);
        copy.setInterpolationDuration(stage.ticks());
        copy.setTransformation(stage.pose());
    }

    /** The block lifted {@code rise} blocks and drawn at {@code scale}, about its own centre, so blocks side by side never touch. */
    private static Transformation pose(float rise, float scale) {
        float half = scale / 2;
        return new Transformation(new Vector3f(-half, 0.5f + rise - half, -half), null, new Vector3f(scale), null);
    }

    /** Removes every copy in the level at once, wherever it is in its flight. */
    static void clear(ServerLevel level) {
        FLIGHTS.removeIf(flight -> flight.copies.stream().anyMatch(copy -> copy.level() == level));
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
