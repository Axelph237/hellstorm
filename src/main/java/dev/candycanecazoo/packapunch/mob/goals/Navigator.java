package dev.candycanecazoo.packapunch.mob.goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

/** How one zombie moves toward its target. Called every tick while it chases; one instance per mob. */
public interface Navigator {
    void tick(PathfinderMob mob, LivingEntity target);

    /** Vanilla navigation, re-pathing every half second. */
    static Navigator vanilla() {
        return new Navigator() {
            private int cooldown;

            @Override
            public void tick(PathfinderMob mob, LivingEntity target) {
                if (--cooldown <= 0) {
                    cooldown = 10;
                    mob.getNavigation().moveTo(target, 1.0);
                }
            }
        };
    }
}
