package dev.candycanecazoo.hellstorm.gen.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.feature.ConfigBlockEntity;
import dev.candycanecazoo.hellstorm.feature.MachineSettings;
import dev.candycanecazoo.hellstorm.gen.layout.Connector;
import dev.candycanecazoo.hellstorm.registry.GenRegistries;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Marks an opening in a room's wall where another room can join it: fill the opening with these, all facing out of the
 * room ({@code FACING} is the way the placer looked). The blocks that touch, facing the same way with the same settings,
 * are one opening. Its settings ({@link Config}): what it becomes when joined, a door or open, and its group. At
 * generation a joined opening is carved or made a door, and one nothing joined is walled with the pack's seal block.
 */
public final class ConnectorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final Pattern GROUP = Pattern.compile("[a-z0-9_]{0,32}");

    /** What the opening becomes when joined, and which openings it fits: an empty group fits any of its size. */
    public record Config(Connector.Kind kind, String group) {
        public static final Config DEFAULT = new Config(Connector.Kind.DOOR, "");
        private static final Codec<Connector.Kind> KIND = Codec.STRING.comapFlatMap(
                name -> Arrays.stream(Connector.Kind.values()).filter(k -> k.name().toLowerCase(Locale.ROOT).equals(name)).findFirst()
                        .map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a connector kind: " + name)),
                k -> k.name().toLowerCase(Locale.ROOT));
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(KIND, "kind", Connector.Kind.DOOR).forGetter(Config::kind),
                Codecs.strictOptional(Codec.STRING, "group", "").forGetter(Config::group)
        ).apply(i, Config::new));
    }

    public static final class Entity extends ConfigBlockEntity<Config> {
        public Entity(BlockPos pos, BlockState state) {
            super(GenRegistries.CONNECTOR_ENTITY.get(), pos, state, Config.CODEC, Config.DEFAULT);
        }

        @Override
        protected Optional<String> problem(Config config) {
            return GROUP.matcher(config.group()).matches() ? Optional.empty()
                    : Optional.of("A group is up to 32 lowercase letters, digits and underscores");
        }
    }

    public ConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            MachineSettings.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
