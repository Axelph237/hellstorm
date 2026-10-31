package dev.candycanecazoo.hellstorm.gen.block;

import dev.candycanecazoo.hellstorm.gen.pack.PlaceholderKind;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A stand-in for a machine in a room being built: the upgrader, a vending machine, a wall-buy, the Mystery Crate or the
 * power switch. Saved with the room, and at generation replaced by its machine, facing the same way, or taken out, as
 * the pack's {@code generation.json} decides. {@code FACING} is the way the machine's front will face: toward whoever
 * placed it.
 */
public final class PlaceholderBlock extends HorizontalDirectionalBlock {
    private final PlaceholderKind kind;

    public PlaceholderBlock(PlaceholderKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    public PlaceholderKind kind() {
        return kind;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
