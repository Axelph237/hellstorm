package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.mob.goals.Navigator;
import dev.candycanecazoo.packapunch.mob.path.FlowField;
import dev.candycanecazoo.packapunch.mob.path.FlowGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * One zombie's steering on its target's flow field. Each tick it aims at the farthest downhill cell it can walk to in a
 * straight line and moves there directly, offset a little sideways so a crowd spreads out. Close in, it goes straight for
 * the target. It opens wooden doors in its way, and falls back to vanilla navigation for a moment when it stops making
 * progress.
 */
public final class FlowNavigator implements Navigator {
    private static final int LOOKAHEAD = 8;
    private static final double CLOSE_SQR = 2.0 * 2.0;
    private static final int STUCK_TICKS = 40;
    private static final int RESCUE_TICKS = 40;

    private final GamePathing pathing;
    private final int tieBreak;
    private final double sideways;
    private final Navigator rescue = Navigator.vanilla();
    private Vec3 progressMark;
    private int sinceProgress;
    private int rescueLeft;

    public FlowNavigator(GamePathing pathing, int seed) {
        this.pathing = pathing;
        this.tieBreak = seed;
        this.sideways = ((seed & 0xff) / 255.0 - 0.5) * 0.6;
    }

    @Override
    public void tick(PathfinderMob mob, LivingEntity target) {
        if (rescueLeft > 0) {
            rescueLeft--;
            rescue.tick(mob, target);
            return;
        }
        if (mob.distanceToSqr(target) < CLOSE_SQR && mob.getSensing().hasLineOfSight(target)) {
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
            return;
        }
        FlowGraph graph = pathing.graph(mob);
        Optional<BlockPos> from = snap(graph, mob.blockPosition());
        Optional<BlockPos> goal = snap(graph, target.blockPosition());
        Optional<FlowField> field = goal.flatMap(g -> pathing.field(mob, target, g));
        Optional<BlockPos> aim = from.flatMap(f -> field.flatMap(fl -> fl.lookahead(f, LOOKAHEAD, tieBreak)));
        if (aim.isEmpty()) {
            rescue.tick(mob, target);
            return;
        }
        pathing.countFlowStep();
        mob.getNavigation().stop();
        openDoorsAhead(mob, field.get().descent(from.get(), 2, tieBreak));
        Vec3 point = Vec3.atBottomCenterOf(aim.get());
        Vec3 heading = point.subtract(mob.position());
        Vec3 side = new Vec3(-heading.z, 0, heading.x).normalize().scale(sideways);
        mob.getMoveControl().setWantedPosition(point.x + side.x, point.y, point.z + side.z, 1.0);
        watchProgress(mob);
    }

    private void watchProgress(PathfinderMob mob) {
        if (progressMark == null || mob.position().distanceToSqr(progressMark) > 0.25) {
            progressMark = mob.position();
            sinceProgress = 0;
        } else if (++sinceProgress >= STUCK_TICKS) {
            sinceProgress = 0;
            rescueLeft = RESCUE_TICKS;
            mob.getJumpControl().jump();
        }
    }

    /** Wooden doors count as open in the grid; the zombie opens one when it's about to walk into it. */
    private static void openDoorsAhead(PathfinderMob mob, List<BlockPos> cells) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos cell : cells) {
            BlockState state = level.getBlockState(cell);
            if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && !door.isOpen(state)) {
                door.setOpen(mob, level, state, cell, true);
            }
        }
    }

    /** Standing on a slab or mid-jump puts a mob's block position off the grid's nodes, so look one block around. */
    public static Optional<BlockPos> snap(FlowGraph graph, BlockPos pos) {
        for (int dy : new int[] {0, -1, 1}) {
            if (graph.indexOf(pos.above(dy)) >= 0) {
                return Optional.of(pos.above(dy));
            }
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (graph.indexOf(pos.relative(d)) >= 0) {
                return Optional.of(pos.relative(d));
            }
        }
        return Optional.empty();
    }
}
