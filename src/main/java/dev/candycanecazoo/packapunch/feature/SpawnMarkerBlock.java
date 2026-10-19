package dev.candycanecazoo.packapunch.feature;

import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.tools.MapEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Where zombies can spawn. Its zone is whichever zone contains it, so moving zones never leaves stale links. */
public final class SpawnMarkerBlock extends Block {
    public SpawnMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
            MapEdits.apply(serverLevel, map -> map.withFeature(pos, FeatureKind.SPAWN));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            MapEdits.apply(serverLevel, map -> map.withoutFeature(pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
