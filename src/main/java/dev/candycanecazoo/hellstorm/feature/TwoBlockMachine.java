package dev.candycanecazoo.hellstorm.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A machine two blocks wide. {@code FACING} is the way its front faces; the halves run side by side across it, from the
 * foot, which holds the block entity and is the one the map indexes, to the head ({@link #along}). Either half sells,
 * and one half without the other is gone too.
 */
public abstract class TwoBlockMachine extends HorizontalDirectionalBlock implements EntityBlock, FeatureBlock {
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;

    protected TwoBlockMachine(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The block entity, made for the foot only. */
    protected abstract BlockEntity newFootEntity(BlockPos pos, BlockState state);

    /**
     * From the foot to the head, for a machine whose front faces {@code facing}: to the left of someone facing the
     * front. The models run from the foot toward the head along +z, with their front on +x.
     */
    public static Direction along(Direction facing) {
        return facing.getClockWise();
    }

    public static Direction along(BlockState state) {
        return along(state.getValue(FACING));
    }

    /**
     * Faces the player, its long side across their view. The other half goes beside the clicked block toward the side of it
     * the player aimed at, or the other way if there's no room there.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction along = along(facing);
        BlockPos pos = context.getClickedPos();
        BlockPos target = context.replacingClickedOnBlock() ? pos : pos.relative(context.getClickedFace().getOpposite());
        Vec3 aim = context.getClickLocation().subtract(Vec3.atCenterOf(target));
        Direction preferred = aim.x * along.getStepX() + aim.z * along.getStepZ() >= 0 ? along : along.getOpposite();
        for (Direction toward : List.of(preferred, preferred.getOpposite())) {
            BlockPos other = pos.relative(toward);
            Level level = context.getLevel();
            if (level.getBlockState(other).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(other)) {
                return defaultBlockState().setValue(FACING, facing).setValue(PART, toward == along ? BedPart.FOOT : BedPart.HEAD);
            }
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BedPart other = state.getValue(PART) == BedPart.FOOT ? BedPart.HEAD : BedPart.FOOT;
            level.setBlock(pos.relative(towardOtherHalf(state)), state.setValue(PART, other), Block.UPDATE_ALL);
        }
    }

    /** Places a whole machine, foot at {@code foot}, for code that doesn't go through a player placing the item. */
    public static void place(Level level, BlockPos foot, BlockState footState) {
        level.setBlock(foot, footState, Block.UPDATE_ALL);
        level.setBlock(foot.relative(along(footState)), footState.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
    }

    private static Direction towardOtherHalf(BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? along(state) : along(state).getOpposite();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == towardOtherHalf(state)) {
            boolean matched = neighbor.is(this) && neighbor.getValue(PART) != state.getValue(PART) && neighbor.getValue(FACING) == state.getValue(FACING);
            return matched ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockPos mainPos(BlockState state, BlockPos pos) {
        return state.getValue(PART) == BedPart.HEAD ? pos.relative(along(state).getOpposite()) : pos;
    }

    @Override
    public List<BlockPos> blocks(BlockState state, BlockPos pos) {
        BlockPos foot = mainPos(state, pos);
        return List.of(foot, foot.relative(along(state)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? newFootEntity(pos, state) : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            FeatureBlock.use(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (state.getValue(PART) == BedPart.FOOT) {
            FeatureBlock.placed(level, pos, state, oldState, featureKind());
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getValue(PART) == BedPart.FOOT) {
            FeatureBlock.removed(level, pos, state, newState);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
