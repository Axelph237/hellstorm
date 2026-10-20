package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.map.StandingArea;
import dev.candycanecazoo.packapunch.map.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Where players spawn: the standing spots of the largest walkable piece of each start zone. Smaller pieces (a box drawn
 * past a wall, say) are left out, and the validator warns about them.
 */
public final class StartCells {
    private StartCells() {}

    public static List<BlockPos> find(ServerLevel level, List<Zone> startZones) {
        List<BlockPos> cells = new ArrayList<>();
        for (Zone zone : startZones) {
            List<List<BlockPos>> pieces = StandingArea.pieces(zone, pos -> standable(level, pos));
            if (!pieces.isEmpty()) {
                cells.addAll(pieces.get(0));
            }
        }
        return cells;
    }

    /** Ground no taller than a full block below (not a fence or wall top), and two blocks of headroom. */
    public static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        net.minecraft.world.phys.shapes.VoxelShape ground = level.getBlockState(below).getCollisionShape(level, below);
        return !ground.isEmpty() && ground.max(net.minecraft.core.Direction.Axis.Y) <= 1.0
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
}
