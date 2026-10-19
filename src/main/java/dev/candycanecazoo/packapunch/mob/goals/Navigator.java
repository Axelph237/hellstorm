package dev.candycanecazoo.packapunch.mob.goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

/** How a zombie walks toward its target. Called a few times a second while it chases. */
@FunctionalInterface
public interface Navigator {
    Navigator VANILLA = (mob, target) -> mob.getNavigation().moveTo(target, 1.0);

    void moveToward(PathfinderMob mob, LivingEntity target);
}
