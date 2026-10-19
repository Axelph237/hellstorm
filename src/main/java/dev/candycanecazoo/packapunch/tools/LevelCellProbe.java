package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.map.CellProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathComputationType;

/** Classifies cells the way a walking mob sees them: open doors and gates pass, closed ones don't. */
public final class LevelCellProbe {
    private LevelCellProbe() {}

    public static CellProbe of(ServerLevel level) {
        return pos -> classify(level, pos);
    }

    public static CellProbe.Cell classify(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return CellProbe.Cell.SOLID;
        }
        BlockState state = level.getBlockState(pos);
        boolean openable = state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
        if (openable && state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)) {
            return CellProbe.Cell.CLOSED_DOOR;
        }
        return state.isPathfindable(level, pos, PathComputationType.LAND) ? CellProbe.Cell.OPEN : CellProbe.Cell.SOLID;
    }
}
