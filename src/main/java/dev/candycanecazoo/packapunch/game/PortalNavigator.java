package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.mob.goals.Navigator;
import dev.candycanecazoo.packapunch.mob.path.MovementClass;
import dev.candycanecazoo.packapunch.mob.path.PassabilityGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * One mob's navigator. Within the target's zone it uses vanilla navigation. Across zones it asks {@link GamePathing}
 * for a plan (off the server thread) but only uses it to pick the next doorway, then lets vanilla navigation walk there
 * from wherever the mob is. Following the planned blocks exactly made every zombie queue along the same line; vanilla
 * re-paths from each mob's own position, cuts corners, and spreads a crowd out.
 */
public final class PortalNavigator implements Navigator {
    private final GamePathing pathing;
    private CompletableFuture<Optional<List<BlockPos>>> pending;
    private ZoneId plannedFrom;
    private ZoneId plannedTo;
    private BlockPos waypoint;

    public PortalNavigator(GamePathing pathing) {
        this.pathing = pathing;
    }

    @Override
    public void moveToward(PathfinderMob mob, LivingEntity target) {
        Optional<ZoneId> mobZone = pathing.zoneAt(mob.blockPosition());
        Optional<ZoneId> targetZone = pathing.zoneAt(target.blockPosition());
        if (mobZone.isEmpty() || targetZone.isEmpty() || mobZone.equals(targetZone)) {
            pending = null;
            waypoint = null;
            VANILLA.moveToward(mob, target);
            return;
        }
        if (pending != null && pending.isDone()) {
            waypoint = pending.getNow(Optional.empty()).flatMap(path -> nextDoorway(path, mobZone.get(), pathing::zoneAt)).orElse(null);
            pending = null;
            if (waypoint != null) {
                pathing.countFollowed();
            }
        }
        boolean stale = !mobZone.get().equals(plannedFrom) || !targetZone.get().equals(plannedTo);
        if (pending == null && (stale || waypoint == null)) {
            request(mob, mobZone.get(), target, targetZone.get());
        }
        if (waypoint != null && !stale) {
            mob.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, 1.0);
        } else {
            VANILLA.moveToward(mob, target);
        }
    }

    private void request(PathfinderMob mob, ZoneId from, LivingEntity target, ZoneId to) {
        GamePathing.Planner planner = pathing.planner(MovementClass.ofHitbox(mob.getBbWidth(), mob.getBbHeight()));
        Optional<BlockPos> start = snap(planner.grids().get(from), mob.blockPosition());
        Optional<BlockPos> goal = snap(planner.grids().get(to), target.blockPosition());
        if (start.isEmpty() || goal.isEmpty()) {
            return;
        }
        plannedFrom = from;
        plannedTo = to;
        waypoint = null;
        pending = pathing.plan(planner, start.get(), from, goal.get(), to);
    }

    /** The first planned cell outside the mob's current zone: the doorway it should walk to next. */
    static Optional<BlockPos> nextDoorway(List<BlockPos> path, ZoneId current, Function<BlockPos, Optional<ZoneId>> zoneAt) {
        return path.stream().filter(cell -> !zoneAt.apply(cell).equals(Optional.of(current))).findFirst();
    }

    /** Standing on a slab or mid-jump puts a mob's block position off the grid's nodes, so look one block around. */
    private static Optional<BlockPos> snap(PassabilityGrid grid, BlockPos pos) {
        if (grid == null) {
            return Optional.empty();
        }
        for (int dy : new int[] {0, -1, 1}) {
            if (grid.walkable(pos.above(dy))) {
                return Optional.of(pos.above(dy));
            }
        }
        return Optional.empty();
    }
}
