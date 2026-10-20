package dev.candycanecazoo.packapunch.tacz;

import dev.candycanecazoo.packapunch.client.ClientPerks;
import dev.candycanecazoo.packapunch.perk.Perks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Speed Cola. TacZ times a reload by how long ago it started: the built-in reload and every gun pack's Lua reload script
 * ask {@code ModernKineticGunScriptAPI.getReloadTime()}. Running that clock faster for a perk holder shortens every kind
 * of reload alike (magazine, shell by shell, interrupted), on the server, which owns the reload.
 */
public final class ReloadTimes {
    private static final String RELOAD_ANIMATION = "reload";

    private ReloadTimes() {}

    /** The reload's elapsed milliseconds as the shooter's perks see them: 0.5 makes a reload take half as long. */
    public static long elapsed(LivingEntity shooter, long millis) {
        double factor = shooter instanceof ServerPlayer player ? Perks.reloadMultiplier(player) : 1;
        return factor == 1 || millis <= 0 ? millis : (long) (millis / factor);
    }

    /**
     * Client only. The local player's first-person reload animations run at the same pace, so the hands finish when the
     * server says the magazine is in. Other animations (draw, inspect, shoot) are untouched.
     */
    public static long animationStep(String animation, long nanos) {
        float factor = ClientPerks.reloadMultiplier();
        return factor == 1 || !animation.startsWith(RELOAD_ANIMATION) ? nanos : (long) (nanos / factor);
    }
}
