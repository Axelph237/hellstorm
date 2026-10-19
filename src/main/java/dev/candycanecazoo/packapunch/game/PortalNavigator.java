package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.mob.goals.Navigator;
import dev.candycanecazoo.packapunch.mob.path.MovementClass;
import dev.candycanecazoo.packapunch.mob.path.PassabilityGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * One mob's navigator. Within the target's zone it uses vanilla navigation, which is fine over short distances.
 * Across zones it asks {@link GamePathing} for a plan through the portals (off the server thread) and follows it,
 * replanning when the target changes zone or the mob gets stuck.
 */
public final class PortalNavigator implements Navigator {
    private final GamePathing pathing;
    private CompletableFuture<Optional<List<BlockPos>>> pending;
    private ZoneId plannedFor;
    private boolean following;

    public PortalNavigator(GamePathing pathing) {
        this.pathing = pathing;
    }

    @Override
    public void moveToward(PathfinderMob mob, LivingEntity target) {
        Optional<ZoneId> mobZone = pathing.zoneAt(mob.blockPosition());
        Optional<ZoneId> targetZone = pathing.zoneAt(target.blockPosition());
        if (mobZone.isEmpty() || targetZone.isEmpty() || mobZone.equals(targetZone)) {
            reset();
            VANILLA.moveToward(mob, target);
            return;
        }
        if (pending != null) {
            if (!pending.isDone()) {
                return;
            }
            Optional<List<BlockPos>> plan = pending.getNow(Optional.empty());
            pending = null;
            following = plan.isPresent() && follow(mob, plan.get());
            if (following) {
                pathing.countFollowed();
            } else {
                VANILLA.moveToward(mob, target);
            }
            return;
        }
        boolean needsPlan = !targetZone.get().equals(plannedFor) || !following || mob.getNavigation().isDone() || mob.getNavigation().isStuck();
        if (!needsPlan) {
            return;
        }
        GamePathing.Planner planner = pathing.planner(MovementClass.ofHitbox(mob.getBbWidth(), mob.getBbHeight()));
        Optional<BlockPos> start = snap(planner.grids().get(mobZone.get()), mob.blockPosition());
        Optional<BlockPos> goal = snap(planner.grids().get(targetZone.get()), target.blockPosition());
        if (start.isEmpty() || goal.isEmpty()) {
            VANILLA.moveToward(mob, target);
            return;
        }
        plannedFor = targetZone.get();
        pending = pathing.plan(planner, start.get(), mobZone.get(), goal.get(), targetZone.get());
    }

    private void reset() {
        pending = null;
        plannedFor = null;
        following = false;
    }

    private static boolean follow(PathfinderMob mob, List<BlockPos> cells) {
        List<Node> nodes = cells.stream().map(p -> new Node(p.getX(), p.getY(), p.getZ())).toList();
        return mob.getNavigation().moveTo(new Path(nodes, cells.get(cells.size() - 1), true), 1.0);
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
