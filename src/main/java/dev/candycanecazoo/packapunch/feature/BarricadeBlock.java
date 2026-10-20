package dev.candycanecazoo.packapunch.feature;

import dev.candycanecazoo.packapunch.game.ForgeGameWorld;
import dev.candycanecazoo.packapunch.game.WindowOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a window opening. All blocks of a window share their {@code boards} value (see {@link WindowOps}).
 * Collision depends on who asks: game mobs pass once the boards are gone, bullets and projectiles always pass (so players
 * can shoot through windows), and players and every other mob are always blocked.
 */
public final class BarricadeBlock extends Block {
    public static final IntegerProperty BOARDS = IntegerProperty.create("boards", 0, 6);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final double PANEL = 3;

    /** The boards' panel, like a trapdoor's, on the inside face (opposite {@code FACING}, which points outward). */
    static VoxelShape panel(Direction outward) {
        return switch (outward) {
            case SOUTH -> Block.box(0, 0, 0, 16, 16, PANEL);
            case EAST -> Block.box(0, 0, 0, PANEL, 16, 16);
            case WEST -> Block.box(16 - PANEL, 0, 0, 16, 16, 16);
            default -> Block.box(0, 0, 16 - PANEL, 16, 16, 16);
        };
    }

    public BarricadeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BOARDS, 6).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOARDS, FACING);
    }

    /**
     * Context-free queries (no entity) see a bare window as open: vanilla's move control asks that way and otherwise makes
     * zombies jump at the bottom block while climbing through. Players are always asked with their own context.
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean bare = state.getValue(BOARDS) == 0;
        Entity entity = context instanceof EntityCollisionContext entityContext ? entityContext.getEntity() : null;
        if (entity == null) {
            return bare ? Shapes.empty() : panel(state.getValue(FACING));
        }
        if (!(entity instanceof LivingEntity) || (bare && entity.getTags().contains(ForgeGameWorld.MOB_TAG))) {
            return Shapes.empty();
        }
        return panel(state.getValue(FACING));
    }

    /** What players aim at to repair: the panel, even with no boards left. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return panel(state.getValue(FACING));
    }

    /** Vanilla pathfinding never routes through windows; zombies use {@code BreakBarricadeGoal} to climb in. */
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            return WindowOps.tryRepair(serverLevel, pos, serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }
}
