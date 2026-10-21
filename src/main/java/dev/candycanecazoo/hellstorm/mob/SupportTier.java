package dev.candycanecazoo.hellstorm.mob;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;

/** How fully the game can drive a mob type. Decided from a live instance, since datapacks name arbitrary entity ids. */
public enum SupportTier {
    /** A goal-driven PathfinderMob: navigation, targeting, and melee all injected. */
    FULL,
    /** Brain-driven (piglin, hoglin, warden, villager): its brain fights our goals for movement, so only augment is allowed. */
    PARTIAL_BRAIN,
    /** A Mob that can't walk paths (ghast, phantom): it counts as a zombie and targets players, but isn't navigated. */
    NO_NAVIGATION,
    /** Not a Mob at all (an item, an armor stand): can't be a zombie. */
    UNSUPPORTED;

    public static SupportTier of(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return UNSUPPORTED;
        }
        if (!mob.getBrain().getMemories().isEmpty()) {
            return PARTIAL_BRAIN;
        }
        return mob instanceof PathfinderMob ? FULL : NO_NAVIGATION;
    }
}
