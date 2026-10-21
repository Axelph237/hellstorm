package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.map.CellProbe;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Classifies cells the way a game zombie sees them. Wooden doors always pass, because game zombies open them. Iron doors,
 * fence gates, and trapdoors pass only while open.
 */
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
        if (state.is(PapRegistries.BARRICADE.get())) {
            return CellProbe.Cell.SOLID;
        }
        if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()) {
            return CellProbe.Cell.OPEN;
        }
        boolean openable = state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
        if (openable && state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)) {
            return CellProbe.Cell.CLOSED_DOOR;
        }
        return state.isPathfindable(level, pos, PathComputationType.LAND) ? CellProbe.Cell.OPEN : CellProbe.Cell.SOLID;
    }
}
