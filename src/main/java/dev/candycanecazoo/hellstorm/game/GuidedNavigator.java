package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.mob.goals.Navigator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * One zombie's way to its target, on vanilla navigation. Near the target it paths straight at it. Far away it paths to
 * the point {@link GamePathing#waypoint} picks down the target's distance field, picked again every half second as it
 * goes, so vanilla only ever plans a short walk and each zombie plans its own from where it stands.
 */
public final class GuidedNavigator implements Navigator {
    private static final int REPATH_TICKS = 10;
    private static final int STUCK_TICKS = 40;
    private static final int YIELD_TICKS = 15;
    /** Closer than this a zombie is fighting, not stuck, when it stands still. */
    private static final double FIGHTING_SQR = 3.0 * 3.0;

    private final GamePathing pathing;
    private final int tieBreak;
    private int repath;
    private Vec3 progressMark;
    private int sinceProgress;

    public GuidedNavigator(GamePathing pathing, int tieBreak) {
        this.pathing = pathing;
        this.tieBreak = tieBreak;
    }

    @Override
    public void tick(PathfinderMob mob, LivingEntity target) {
        watchProgress(mob, target);
        if (--repath > 0) {
            return;
        }
        if (sinceProgress >= YIELD_TICKS && behindAnother(mob, target)) {
            // Two zombies side by side at a narrow opening shove each other into its jambs, and neither gets through. The
            // one farther from the target stands for a moment, so the other pushes past it.
            mob.getNavigation().stop();
            repath = YIELD_TICKS;
            return;
        }
        repath = REPATH_TICKS;
        Optional<BlockPos> waypoint = pathing.waypoint(mob, target, tieBreak);
        if (waypoint.isPresent()) {
            pathing.countGuidedLeg();
            Vec3 at = Vec3.atBottomCenterOf(waypoint.get());
            if (mob.getNavigation().moveTo(at.x, at.y, at.z, 1.0)) {
                return;
            }
        }
        mob.getNavigation().moveTo(target, 1.0);
    }

    /** Whether another mob chasing the same target, close enough to be shoving this one, is nearer to it. */
    private static boolean behindAnother(PathfinderMob mob, LivingEntity target) {
        double distance = mob.distanceToSqr(target);
        return !mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(0.5),
                other -> other != mob && other.getTarget() == target && other.distanceToSqr(target) < distance).isEmpty();
    }

    private void watchProgress(PathfinderMob mob, LivingEntity target) {
        if (progressMark == null || mob.position().distanceToSqr(progressMark) > 0.25 || mob.distanceToSqr(target) < FIGHTING_SQR) {
            progressMark = mob.position();
            sinceProgress = 0;
        } else if (++sinceProgress >= STUCK_TICKS) {
            sinceProgress = 0;
            pathing.countStall();
            repath = 0;
        }
    }
}
