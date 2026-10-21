package dev.candycanecazoo.hellstorm.feature;

import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.List;

/**
 * Where zombies can spawn. Its zone is whichever zone contains it, so moving zones never leaves stale links. {@code hidden}
 * while a game runs on its level, from the game registering to {@link dev.candycanecazoo.hellstorm.game.MapReset} after it
 * ends: drawn as nothing, with no outline to target.
 */
public final class SpawnMarkerBlock extends Block {
    public static final BooleanProperty HIDDEN = BooleanProperty.create("hidden");

    public SpawnMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HIDDEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HIDDEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(HIDDEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HIDDEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
            MapEdits.apply(serverLevel, map -> map.withFeature(pos, FeatureKind.SPAWN));
            setHidden(serverLevel, List.of(pos), GameManager.get(serverLevel).isPresent());
        }
    }

    /** Hides or shows the markers at {@code positions}. Positions that hold no marker, or one already that way, are left alone. */
    public static void setHidden(ServerLevel level, Collection<BlockPos> positions, boolean hidden) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SpawnMarkerBlock && state.getValue(HIDDEN) != hidden) {
                level.setBlock(pos, state.setValue(HIDDEN, hidden), Block.UPDATE_CLIENTS);
            }
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
