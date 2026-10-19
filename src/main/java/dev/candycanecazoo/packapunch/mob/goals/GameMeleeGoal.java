package dev.candycanecazoo.packapunch.mob.goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks to the target and hits it for the profile's damage. Doesn't read the attack-damage attribute, which many
 * non-hostile and modded mobs lack, so any PathfinderMob can be a zombie.
 */
public final class GameMeleeGoal extends Goal {
    private static final int ATTACK_INTERVAL = 20;
    private static final int REPATH_TICKS = 10;

    private final PathfinderMob mob;
    private final float damage;
    private int attackCooldown;
    private int repathCooldown;

    public GameMeleeGoal(PathfinderMob mob, float damage) {
        this.mob = mob;
        this.damage = damage;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30f, 30f);
        if (--repathCooldown <= 0) {
            repathCooldown = REPATH_TICKS;
            mob.getNavigation().moveTo(target, 1.0);
        }
        attackCooldown = Math.max(0, attackCooldown - 1);
        if (attackCooldown == 0 && mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
            attackCooldown = ATTACK_INTERVAL;
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            target.hurt(mob.damageSources().mobAttack(mob), damage);
        }
    }
}
