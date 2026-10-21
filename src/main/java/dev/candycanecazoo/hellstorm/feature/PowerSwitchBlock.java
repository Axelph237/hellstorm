package dev.candycanecazoo.hellstorm.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.Refusal;
import dev.candycanecazoo.hellstorm.game.Shop;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Turns the map's power on for the rest of the game. A lever that only a game can pull: it stays down until the map is
 * reset, and like a lever it powers redstone, so map makers can wire lights and doors to it.
 */
public final class PowerSwitchBlock extends LeverBlock implements EntityBlock, FeatureBlock {
    public record Config(int cost) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost", 0).forGetter(Config::cost)
        ).apply(i, Config::new));
    }

    public static final class Entity extends ConfigBlockEntity<Config> implements Prompted {
        public Entity(BlockPos pos, BlockState state) {
            super(PapRegistries.POWER_SWITCH_ENTITY.get(), pos, state, Config.CODEC, new Config(0));
        }

        @Override
        public Optional<Prompt> prompt() {
            return getBlockState().getValue(POWERED) ? Optional.empty() : Optional.of(new Prompt("Power", config().cost(), false));
        }
    }

    public PowerSwitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.POWER_SWITCH;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            FeatureBlock.use(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void buy(ServerPlayer player, GameManager.Active game, BlockPos pos, BlockState state) {
        int cost = config(game.level(), pos).cost();
        Shop.buy(player, game, new Shop.Sale() {
            @Override
            public int cost() {
                return cost;
            }

            @Override
            public boolean needsPower() {
                return false;
            }

            @Override
            public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                return g.game().powerOn() ? Optional.of(new Refusal.Because("The power is already on")) : Optional.empty();
            }

            @Override
            public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                g.game().turnPowerOn(Optional.of(p.getUUID()));
                setAll(g.level(), g.game().map().map().features(FeatureKind.POWER_SWITCH), true);
                g.world().announce("The power is on");
                return Optional.empty();
            }
        });
    }

    /** Flips every power switch in {@code switches} on or off, updating redstone like a lever. */
    public static void setAll(ServerLevel level, java.util.Collection<BlockPos> switches, boolean on) {
        for (BlockPos pos : switches) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof PowerSwitchBlock block && state.getValue(POWERED) != on) {
                block.pull(state, level, pos);
            }
        }
    }

    public static Config config(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Entity entity ? entity.config() : new Config(0);
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos) {
        int cost = config(level, pos).cost();
        return Component.literal("Power switch: " + (cost == 0 ? "free" : cost + " points") + ". Set with /hs power cost <points>");
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        FeatureBlock.placed(level, pos, state, oldState, featureKind());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        FeatureBlock.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
