package dev.candycanecazoo.hellstorm.mob;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * A runner moves at least as fast as a walking player: its speed is raised to {@link #SPEED}, unless its round and
 * profile already make it faster. A crawler's slowdown applies on top, so a crawler that runs moves 0.8 times as fast as that.
 * Marked by {@value #RUNNER} in its NBT ({@code ForgeData}), so a profile's {@code nbt} can make one.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class Runners {
    public static final String RUNNER = Hellstorm.MOD_ID + "_runner";
    /**
     * The speed attribute at which a mob walks 4.32 blocks a second on flat ground, as a walking player does. A mob's
     * ground speed goes with the square of its attribute ({@link GroundSpeed}), so this is about the square root of 0.1, a
     * player's own 0.1 walking speed. Measured by the GameTest {@code aRunnerWalksAsFastAsAWalkingPlayer}.
     */
    public static final double SPEED = 0.316;
    private static final UUID RUN_SPEED = UUID.fromString("c2a7e0b4-5d1f-4e8b-a6c3-9f4d2b7e1a58");

    private Runners() {}

    public static boolean runs(Mob mob) {
        return mob.getPersistentData().getBoolean(RUNNER);
    }

    /** Makes {@code mob} a runner, unless it already is. */
    public static void run(Mob mob) {
        mob.getPersistentData().putBoolean(RUNNER, true);
        applyRun(mob);
    }

    private static void applyRun(Mob mob) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(RUN_SPEED) == null && speed.getBaseValue() < SPEED) {
            speed.addPermanentModifier(new AttributeModifier(RUN_SPEED, "Hellstorm runner", SPEED - speed.getBaseValue(), AttributeModifier.Operation.ADDITION));
        }
    }

    /** A mob loaded or spawned with the runner tag runs from the start. */
    @SubscribeEvent
    public static void onRunnerJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && runs(mob)) {
            applyRun(mob);
        }
    }
}
