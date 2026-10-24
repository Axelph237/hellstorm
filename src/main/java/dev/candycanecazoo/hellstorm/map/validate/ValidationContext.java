package dev.candycanecazoo.hellstorm.map.validate;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.map.CellProbe;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * Everything validation needs besides the map: how to read the world, where a player can stand, the window rules, the
 * size of every mob that might have to climb through a window, and problems with what the map depends on but doesn't
 * hold (found by {@code PunchAudit} and {@code ContentAudit}, which need the loaded datapacks and the world).
 */
public record ValidationContext(CellProbe probe, Predicate<BlockPos> standable, int maxPassageGap, GameSettings.Windows windows,
                                List<MobSize> mobs, List<Problem> itemProblems) {
    /** A mob profile's hitbox, from its entity type. */
    public record MobSize(String profile, float width, float height) {}

    public static final ValidationContext OFFLINE = new ValidationContext(CellProbe.ALL_SOLID, pos -> false, MapValidator.DEFAULT_MAX_GAP,
            GameSettings.DEFAULT.windows(), List.of());

    public ValidationContext {
        mobs = List.copyOf(mobs);
        itemProblems = List.copyOf(itemProblems);
    }

    public ValidationContext(CellProbe probe, Predicate<BlockPos> standable, int maxPassageGap, GameSettings.Windows windows, List<MobSize> mobs) {
        this(probe, standable, maxPassageGap, windows, mobs, List.of());
    }
}
