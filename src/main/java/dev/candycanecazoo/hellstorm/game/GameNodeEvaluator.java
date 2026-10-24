package dev.candycanecazoo.hellstorm.game;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Predicate;

/**
 * Vanilla's walk evaluator with two moves taken away: ones the mob's box can't actually make, and ones into the blocks of
 * a door nobody has bought. Game zombies' navigation and the game's distance fields ({@link VanillaGraphs}) both use it, so
 * the walk a zombie is guided along is one vanilla navigation can follow.
 */
class GameNodeEvaluator extends WalkNodeEvaluator {
    private Predicate<BlockPos> shut = pos -> false;

    void shut(Predicate<BlockPos> shut) {
        this.shut = shut;
    }

    @Override
    public int getNeighbors(Node[] out, Node node) {
        int count = super.getNeighbors(out, node);
        int kept = 0;
        for (int i = 0; i < count; i++) {
            Node next = out[i];
            if (!inShutDoor(next) && clear(node.asBlockPos(), next.asBlockPos())) {
                out[kept++] = next;
            }
        }
        return kept;
    }

    private boolean inShutDoor(Node node) {
        int width = Mth.ceil(mob.getBbWidth());
        int height = Mth.ceil(mob.getBbHeight());
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                for (int dy = 0; dy < height; dy++) {
                    if (shut.test(new BlockPos(node.x + dx, node.y + dy, node.z + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether the mob's box can slide from one node's centre to the other's without touching a block above step height.
     * Vanilla's evaluator treats every open trapdoor as walkable whichever side its panel is on, and doesn't check a
     * move's sweep against thin shapes, so a wall of open trapdoors looks like a doorway to it. Checks the first half of
     * the move at the starting floor and the second half at the destination's. A closed door the mob opens doesn't count:
     * it will be open by the time the mob gets there. Moves through cells with no shapes skip the sweep.
     */
    private boolean clear(BlockPos from, BlockPos to) {
        double fromFloor = WalkNodeEvaluator.getFloorLevel(level, from);
        double toFloor = WalkNodeEvaluator.getFloorLevel(level, to);
        double step = mob.maxUpStep();
        double height = mob.getBbHeight();
        int minY = Mth.floor(Math.min(fromFloor, toFloor) + step);
        int maxY = Mth.floor(Math.max(fromFloor, toFloor) + height);
        if (!anyShape(from.getX(), from.getZ(), minY, maxY) && !anyShape(to.getX(), to.getZ(), minY, maxY)
                && !anyShape(from.getX(), to.getZ(), minY, maxY) && !anyShape(to.getX(), from.getZ(), minY, maxY)) {
            return true;
        }
        double half = mob.getBbWidth() / 2 - 1.0E-3;
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        int samples = Math.max(2, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 0.2));
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double x = from.getX() + 0.5 + dx * t;
            double z = from.getZ() + 0.5 + dz * t;
            double floor = t < 0.5 ? fromFloor : toFloor;
            if (collides(new AABB(x - half, floor + step, z - half, x + half, floor + height - 1.0E-3, z + half))) {
                return false;
            }
        }
        return true;
    }

    private boolean collides(AABB box) {
        VoxelShape swept = Shapes.create(box);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            VoxelShape shape = shape(pos);
            if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()), swept, BooleanOp.AND)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyShape(int x, int z, int minY, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            if (!shape(pos.set(x, y, z)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private VoxelShape shape(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (canOpenDoors && state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && !door.isOpen(state)) {
            return Shapes.empty();
        }
        return state.getCollisionShape(level, pos);
    }
}
