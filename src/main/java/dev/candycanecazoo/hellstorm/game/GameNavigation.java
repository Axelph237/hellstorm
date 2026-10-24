package dev.candycanecazoo.hellstorm.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Vanilla ground navigation on the game's walk evaluator ({@link GameNodeEvaluator}), with one change to how it follows a
 * path. Vanilla turns for the node after next as soon as it is within half a block of the next one, or heading past it,
 * which walks a mob into the jamb of a doorway it turns into, where the crowd behind pins it. This turns only when the
 * mob's box can slide straight to the node after next, and otherwise walks on to the next node's centre first. A mob
 * the crowd shoves off that straight line aims back at the node it skipped.
 */
final class GameNavigation extends GroundPathNavigation {
    /** Like vanilla, a node is only skipped once the mob is this close to it. */
    private static final double CUT_RANGE_SQR = 2.0 * 2.0;
    /** Close enough to a node's centre to turn for the next whatever is in the way. */
    private static final double CENTRED = 0.2;
    /** Box sweeps sample this far apart: well under a zombie's width, so no panel slips between samples. */
    private static final double SWEEP_STEP = 0.2;

    private GameNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new GameNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected void followThePath() {
        Vec3 at = getTempMobPos();
        maxDistanceToWaypoint = mob.getBbWidth() > 0.75F ? mob.getBbWidth() / 2.0F : 0.75F - mob.getBbWidth() / 2.0F;
        stepBackIfPushedOffLine(at);
        Vec3i next = path.getNextNodePos();
        double dx = Math.abs(mob.getX() - (next.getX() + 0.5));
        double dz = Math.abs(mob.getZ() - (next.getZ() + 0.5));
        boolean level = Math.abs(mob.getY() - next.getY()) < 1.0;
        boolean near = dx < maxDistanceToWaypoint && dz < maxDistanceToWaypoint && level;
        boolean centred = dx < CENTRED && dz < CENTRED && level;
        boolean approaching = canCutCorner(path.getNextNode().type) && at.distanceToSqr(Vec3.atBottomCenterOf(next)) <= CUT_RANGE_SQR;
        if ((near || approaching) && clearPastNextNode(near, centred)) {
            path.advance();
        }
        doStuckDetection(at);
    }

    /**
     * A mob that turned for a node it could slide straight to, then got shoved off that line by the crowd, aims at the
     * node it skipped again instead of pressing into a jamb.
     */
    private void stepBackIfPushedOffLine(Vec3 at) {
        int index = path.getNextNodeIndex();
        if (index == 0) {
            return;
        }
        BlockPos skipped = path.getNodePos(index - 1);
        BlockPos next = path.getNodePos(index);
        if (skipped.getY() == next.getY() && Math.abs(mob.getY() - next.getY()) < 1.0
                && at.distanceToSqr(Vec3.atBottomCenterOf(skipped)) <= CUT_RANGE_SQR && !slides(next)) {
            path.setNextNodeIndex(index - 1);
        }
    }

    /**
     * Whether to aim past the next node now. On the flat, only once the mob's box can slide straight to the node after
     * it, or it stands on the node's centre; a step up or down, or the path's end, keeps vanilla's rule.
     */
    private boolean clearPastNextNode(boolean near, boolean centred) {
        int after = path.getNextNodeIndex() + 1;
        if (after >= path.getNodeCount() || path.getNodePos(after).getY() != path.getNextNodePos().getY()) {
            return near;
        }
        return centred || slides(path.getNodePos(after));
    }

    /**
     * Whether the mob's box, above step height, can slide straight from where it stands to the centre of {@code node}. A
     * crowd can shove a mob a little into a wall; from there nothing can be judged, so it counts as clear.
     */
    private boolean slides(BlockPos node) {
        double half = mob.getBbWidth() / 2 - 1.0E-3;
        double bottom = mob.getY() + mob.maxUpStep();
        double top = mob.getY() + mob.getBbHeight() - 1.0E-3;
        if (!this.level.noCollision(mob, new AABB(mob.getX() - half, bottom, mob.getZ() - half, mob.getX() + half, top, mob.getZ() + half))) {
            return true;
        }
        double dx = node.getX() + 0.5 - mob.getX();
        double dz = node.getZ() + 0.5 - mob.getZ();
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / SWEEP_STEP));
        for (int i = 1; i <= samples; i++) {
            double x = mob.getX() + dx * i / samples;
            double z = mob.getZ() + dz * i / samples;
            if (!this.level.noCollision(mob, new AABB(x - half, bottom, z - half, x + half, top, z + half))) {
                return false;
            }
        }
        return true;
    }

    /** Only plain ground navigation is replaced; a mob with its own kind (climbers, swimmers, modded) keeps it. */
    static boolean canReplace(Mob mob) {
        return mob.getNavigation().getClass() == GroundPathNavigation.class;
    }

    /**
     * Gives the mob this navigation, keeping what its old one could do. Its search budget comes from the mob's follow range
     * now, so call this after the game has set it.
     */
    static void install(Mob mob, Predicate<BlockPos> shut) {
        PathNavigation old = mob.getNavigation();
        NodeEvaluator was = old.getNodeEvaluator();
        GameNavigation navigation = new GameNavigation(mob, mob.level());
        ((GameNodeEvaluator) navigation.getNodeEvaluator()).shut(shut);
        navigation.setCanOpenDoors(was.canOpenDoors());
        navigation.setCanPassDoors(was.canPassDoors());
        navigation.setCanFloat(was.canFloat());
        navigation.setCanWalkOverFences(was.canWalkOverFences());
        old.stop();
        mob.navigation = navigation;
    }
}
