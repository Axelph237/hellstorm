package dev.candycanecazoo.packapunch.mob.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Gets a zombie from its spawn outside the map into the zone: walk to the window, tear boards off, climb through.
 * Boards come back if a player repairs mid-climb, and the zombie goes back to tearing. Ends once the zombie is inside.
 */
public final class BreakBarricadeGoal extends Goal {
    /** What the goal needs from the window, so it doesn't depend on how windows are stored. */
    public interface Window {
        /** Where this zombie stands outside to tear boards. Each zombie gets its own spot, so a crowd spreads along the window. */
        Vec3 attackPoint();

        /** Where this zombie stands in the opening itself: on top of the sill, if the window has one. */
        Vec3 sillPoint();

        /** Where this zombie ends up after climbing through. */
        Vec3 insidePoint();

        int boards();

        void tearBoard();
    }

    private static final double REACH_SQR = 1.6 * 1.6;

    private final PathfinderMob mob;
    private final Window window;
    private final int tearTicks;
    private final Predicate<BlockPos> insideZone;
    private int tearProgress;
    private int repathCooldown;

    public BreakBarricadeGoal(PathfinderMob mob, Window window, int tearTicks, Predicate<BlockPos> insideZone) {
        this.mob = mob;
        this.window = window;
        this.tearTicks = tearTicks;
        this.insideZone = insideZone;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return !insideZone.test(mob.blockPosition());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    @Override
    public void tick() {
        Vec3 attack = window.attackPoint();
        Vec3 inside = window.insidePoint();
        mob.getLookControl().setLookAt(inside.x, inside.y + 1, inside.z);
        if (window.boards() == 0) {
            mob.getNavigation().stop();
            // Aim at the sill until the zombie is over it. A target above the zombie's feet is what makes vanilla's
            // move control jump, so aiming straight at the inside floor would walk it into a raised sill forever.
            Vec3 sill = window.sillPoint();
            Vec3 aim = horizontalDistanceSqr(mob.position(), inside) <= horizontalDistanceSqr(sill, inside) + 0.05 ? inside : sill;
            mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 1.0);
            return;
        }
        if (mob.position().distanceToSqr(attack) > REACH_SQR) {
            tearProgress = 0;
            if (--repathCooldown <= 0) {
                repathCooldown = 10;
                if (!mob.getNavigation().moveTo(attack.x, attack.y, attack.z, 1.0)) {
                    mob.getMoveControl().setWantedPosition(attack.x, attack.y, attack.z, 1.0);
                }
            }
            return;
        }
        mob.getNavigation().stop();
        if (++tearProgress >= tearTicks) {
            tearProgress = 0;
            mob.swing(InteractionHand.MAIN_HAND);
            window.tearBoard();
        }
    }
}
