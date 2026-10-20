package dev.candycanecazoo.packapunch.map.validate;

import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.map.CellProbe;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * Everything validation needs besides the map: how to read the world, where a player can stand, the window rules, and the
 * size of every mob that might have to climb through a window.
 */
public record ValidationContext(CellProbe probe, Predicate<BlockPos> standable, int maxPassageGap, GameSettings.Windows windows,
                                List<MobSize> mobs) {
    /** A mob profile's hitbox, from its entity type. */
    public record MobSize(String profile, float width, float height) {}

    public static final ValidationContext OFFLINE = new ValidationContext(CellProbe.ALL_SOLID, pos -> false, MapValidator.DEFAULT_MAX_GAP,
            GameSettings.DEFAULT.windows(), List.of());

    public ValidationContext {
        mobs = List.copyOf(mobs);
    }
}
