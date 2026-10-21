package dev.candycanecazoo.hellstorm.tacz;

import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * The {@code hellstorm:reload_speed} attribute applied to TacZ. TacZ times a reload by how long ago it started: the
 * built-in reload and every gun pack's Lua reload script ask {@code ModernKineticGunScriptAPI.getReloadTime()}. Running
 * that clock at the shooter's reload speed shortens every kind of reload alike (magazine, shell by shell, interrupted),
 * on the server, which owns the reload. The attribute syncs to the player's client, which plays its reload animations
 * at the same pace.
 */
public final class ReloadTimes {
    private static final String RELOAD_ANIMATION = "reload";

    private ReloadTimes() {}

    /** 1 for anything without the attribute (mobs holding guns). */
    public static double speed(LivingEntity shooter) {
        AttributeInstance instance = shooter.getAttribute(PapRegistries.RELOAD_SPEED);
        return instance == null ? 1 : instance.getValue();
    }

    /** The reload's elapsed milliseconds at the shooter's reload speed: at 2, a reload takes half as long. */
    public static long elapsed(LivingEntity shooter, long millis) {
        double speed = speed(shooter);
        return speed == 1 || millis <= 0 ? millis : (long) (millis * speed);
    }

    /** One step of a first-person animation. Reload animations run at {@code speed}; draw, inspect, and shoot don't. */
    public static long animationStep(String animation, long nanos, double speed) {
        return speed == 1 || !animation.startsWith(RELOAD_ANIMATION) ? nanos : (long) (nanos * speed);
    }
}
