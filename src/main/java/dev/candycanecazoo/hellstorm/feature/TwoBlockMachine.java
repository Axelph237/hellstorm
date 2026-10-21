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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A machine two blocks wide. {@code FACING} is the way its front faces; the halves run side by side across it, from the
 * foot, which holds the block entity and is the one the map indexes, to the head ({@link #along}). A machine whose block
 * adds {@link #HALF} to its state is two blocks tall as well, its upper blocks over the lower ones, and the lower foot is
 * the main one. Any block of it sells, and the machine is gone when any block of it is.
 */
public abstract class TwoBlockMachine extends HorizontalDirectionalBlock implements EntityBlock, FeatureBlock {
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** One block of a machine and its state. */
    private record Piece(BlockPos pos, BlockState state) {}

    protected TwoBlockMachine(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The block entity, made for the main block only. */
    protected abstract BlockEntity newFootEntity(BlockPos pos, BlockState state);

    /**
     * From the foot to the head, for a machine whose front faces {@code facing}: to the left of someone facing the
     * front.
     */
    public static Direction along(Direction facing) {
        return facing.getClockWise();
    }

    public static Direction along(BlockState state) {
        return along(state.getValue(FACING));
    }

    private static boolean tall(BlockState state) {
        return state.hasProperty(HALF);
    }

    /** The block that holds the block entity: the foot, and the lower one of a tall machine. */
    private static boolean isMain(BlockState state) {
        return state.getValue(PART) == BedPart.FOOT && (!tall(state) || state.getValue(HALF) == DoubleBlockHalf.LOWER);
    }

    /**
     * Faces the player, its long side across their view. The other half goes beside the clicked block toward the side of it
     * the player aimed at, or the other way if there's no room there. A tall machine needs room above both halves too.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction along = along(facing);
        BlockPos pos = context.getClickedPos();
        BlockPos target = context.replacingClickedOnBlock() ? pos : pos.relative(context.getClickedFace().getOpposite());
        Vec3 aim = context.getClickLocation().subtract(Vec3.atCenterOf(target));
        Direction preferred = aim.x * along.getStepX() + aim.z * along.getStepZ() >= 0 ? along : along.getOpposite();
        BlockState placed = defaultBlockState().setValue(FACING, facing);
        boolean tall = tall(placed);
        for (Direction toward : List.of(preferred, preferred.getOpposite())) {
            BlockPos other = pos.relative(toward);
            if (fits(context, other) && (!tall || fits(context, pos.above()) && fits(context, other.above()))) {
                return placed.setValue(PART, toward == along ? BedPart.FOOT : BedPart.HEAD);
            }
        }
        return null;
    }

    private static boolean fits(BlockPlaceContext context, BlockPos pos) {
        Level level = context.getLevel();
        return pos.getY() < level.getMaxBuildHeight() && level.getBlockState(pos).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            for (Piece piece : parts(mainPos(state, pos), state)) {
                if (!piece.pos().equals(pos)) {
                    level.setBlock(piece.pos(), piece.state(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Places a whole machine, main block at {@code foot}, for code that doesn't go through a player placing the item. */
    public static void place(Level level, BlockPos foot, BlockState footState) {
        for (Piece piece : parts(foot, footState)) {
            level.setBlock(piece.pos(), piece.state(), Block.UPDATE_ALL);
        }
    }

    /** Every block of the machine whose main block is at {@code foot}, the main block first, from any block's state. */
    private static List<Piece> parts(BlockPos foot, BlockState state) {
        BlockState footState = state.setValue(PART, BedPart.FOOT);
        if (tall(state)) {
            footState = footState.setValue(HALF, DoubleBlockHalf.LOWER);
        }
        BlockPos head = foot.relative(along(state));
        List<Piece> parts = new ArrayList<>(List.of(new Piece(foot, footState), new Piece(head, footState.setValue(PART, BedPart.HEAD))));
        if (tall(state)) {
            parts.add(new Piece(foot.above(), footState.setValue(HALF, DoubleBlockHalf.UPPER)));
            parts.add(new Piece(head.above(), footState.setValue(PART, BedPart.HEAD).setValue(HALF, DoubleBlockHalf.UPPER)));
        }
        return parts;
    }

    private static Direction towardOtherHalf(BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? along(state) : along(state).getOpposite();
    }

    /** Toward the block above or below that's part of the same machine, for a tall one. */
    private static Direction towardOtherLevel(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
    }

    /**
     * Gone when the neighbor that's part of it isn't. Only the part's place in the machine is compared, so a machine changing
     * some other property one block at a time stays whole.
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean beside = direction == towardOtherHalf(state);
        boolean overOrUnder = tall(state) && direction == towardOtherLevel(state);
        if (beside || overOrUnder) {
            boolean matched = neighbor.is(this) && neighbor.getValue(FACING) == state.getValue(FACING)
                    && (beside ? neighbor.getValue(PART) != state.getValue(PART) : neighbor.getValue(PART) == state.getValue(PART))
                    && (!tall(state) || (beside ? neighbor.getValue(HALF) == state.getValue(HALF) : neighbor.getValue(HALF) != state.getValue(HALF)));
            return matched ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockPos mainPos(BlockState state, BlockPos pos) {
        BlockPos lower = tall(state) && state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return state.getValue(PART) == BedPart.HEAD ? lower.relative(along(state).getOpposite()) : lower;
    }

    @Override
    public List<BlockPos> blocks(BlockState state, BlockPos pos) {
        return parts(mainPos(state, pos), state).stream().map(Piece::pos).toList();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMain(state) ? newFootEntity(pos, state) : null;
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
        if (isMain(state)) {
            FeatureBlock.placed(level, pos, state, oldState, featureKind());
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (isMain(state)) {
            FeatureBlock.removed(level, pos, state, newState);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
