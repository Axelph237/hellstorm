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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * One zombie's steering on its target's flow field. It aims at the farthest downhill cell its box can slide to in a
 * straight line and moves there directly, offset a little sideways when there's room so a crowd spreads out. Close in, it goes straight for
 * the target. It opens wooden doors in its way, and falls back to vanilla navigation for a moment when it stops making
 * progress.
 */
public final class FlowNavigator implements Navigator {
    private static final int LOOKAHEAD = 8;
    private static final double CLOSE_SQR = 2.0 * 2.0;
    private static final int STUCK_TICKS = 40;
    private static final int RESCUE_TICKS = 40;
    /** The aim is re-chosen this often; sweeping boxes every tick for every zombie isn't needed to steer well. */
    private static final int AIM_TICKS = 4;
    /** Box sweeps sample this far apart: well under a zombie's width, so no panel slips between samples. */
    private static final double SWEEP_STEP = 0.4;

    private final GamePathing pathing;
    private final int tieBreak;
    private final double sideways;
    private final Navigator rescue = Navigator.vanilla();
    private Vec3 progressMark;
    private int sinceProgress;
    private int rescueLeft;
    private Vec3 aim;
    private int aimAge;

    public FlowNavigator(GamePathing pathing, int seed) {
        this.pathing = pathing;
        this.tieBreak = seed;
        this.sideways = ((seed & 0xff) / 255.0 - 0.5) * 0.6;
    }

    @Override
    public void tick(PathfinderMob mob, LivingEntity target) {
        if (rescueLeft > 0) {
            aim = null;
            rescueLeft--;
            rescue.tick(mob, target);
            return;
        }
        if (mob.distanceToSqr(target) < CLOSE_SQR && mob.getSensing().hasLineOfSight(target)) {
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
            aim = null;
            return;
        }
        FlowGraph graph = pathing.graph(mob);
        Optional<BlockPos> from = snap(graph, mob.blockPosition());
        Optional<BlockPos> goal = snap(graph, target.blockPosition());
        Optional<FlowField> field = goal.flatMap(g -> pathing.field(mob, target, g));
        List<BlockPos> ahead = from.isPresent() && field.isPresent() ? field.get().descent(from.get(), LOOKAHEAD, tieBreak) : List.of();
        if (ahead.isEmpty()) {
            aim = null;
            rescue.tick(mob, target);
            return;
        }
        pathing.countFlowStep();
        mob.getNavigation().stop();
        openDoorsAhead(mob, ahead.subList(0, Math.min(2, ahead.size())));
        if (aim == null || --aimAge <= 0 || mob.position().distanceToSqr(aim.x, mob.getY(), aim.z) < 0.25) {
            aim = aim(mob, from.get(), ahead);
            aimAge = AIM_TICKS;
        }
        mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 1.0);
        watchProgress(mob);
    }

    /**
     * The farthest of the next downhill cells on this level that the zombie's own box can slide to in a straight line
     * from where it stands, checked against real block shapes (door jambs, trapdoor panels, fence posts). Offset sideways
     * when that is clear too. A step up or down is taken one cell at a time. If even the next cell can't be reached
     * directly, the zombie first re-centres in its own cell.
     */
    private Vec3 aim(PathfinderMob mob, BlockPos from, List<BlockPos> ahead) {
        Vec3 best = null;
        for (BlockPos cell : ahead) {
            Vec3 point = Vec3.atBottomCenterOf(cell);
            if (cell.getY() != from.getY() || !clearTo(mob, point)) {
                break;
            }
            best = point;
        }
        if (best == null) {
            BlockPos next = ahead.get(0);
            return next.getY() != from.getY() ? Vec3.atBottomCenterOf(next) : Vec3.atBottomCenterOf(from);
        }
        Vec3 heading = best.subtract(mob.position());
        Vec3 offset = best.add(new Vec3(-heading.z, 0, heading.x).normalize().scale(sideways));
        return clearTo(mob, offset) ? offset : best;
    }

    private static boolean clearTo(PathfinderMob mob, Vec3 point) {
        double half = mob.getBbWidth() / 2 - 1.0E-3;
        double bottom = mob.getY() + mob.maxUpStep();
        double top = mob.getY() + mob.getBbHeight() - 1.0E-3;
        double dx = point.x - mob.getX();
        double dz = point.z - mob.getZ();
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / SWEEP_STEP));
        for (int i = 1; i <= samples; i++) {
            double x = mob.getX() + dx * i / samples;
            double z = mob.getZ() + dz * i / samples;
            if (!mob.level().noCollision(mob, new AABB(x - half, bottom, z - half, x + half, top, z + half))) {
                return false;
            }
        }
        return true;
    }

    private void watchProgress(PathfinderMob mob) {
        if (progressMark == null || mob.position().distanceToSqr(progressMark) > 0.25) {
            progressMark = mob.position();
            sinceProgress = 0;
        } else if (++sinceProgress >= STUCK_TICKS) {
            sinceProgress = 0;
            pathing.countStall();
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
