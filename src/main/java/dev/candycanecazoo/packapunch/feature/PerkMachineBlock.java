package dev.candycanecazoo.packapunch.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.client.ClientPerks;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.Refusal;
import dev.candycanecazoo.packapunch.game.Shop;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.perk.PerkDef;
import dev.candycanecazoo.packapunch.perk.PerkDefs;
import dev.candycanecazoo.packapunch.perk.Perks;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

import java.util.Optional;

/** Sells one perk. The machine is tinted with its perk's color. */
public final class PerkMachineBlock extends HorizontalDirectionalBlock implements EntityBlock, FeatureBlock {
    public record Config(Optional<ResourceLocation> perk) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(ResourceLocation.CODEC, "perk").forGetter(Config::perk)
        ).apply(i, Config::new));
    }

    /**
     * What clients need to draw and prompt for the machine. Clients have no datapack perks, so the server resolves the perk
     * each time it sends the machine; it is never saved.
     */
    public record Display(ResourceLocation perk, String name, int color, int cost, boolean requiresPower) {
        public static final Codec<Display> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("perk").forGetter(Display::perk),
                Codec.STRING.fieldOf("name").forGetter(Display::name),
                Codec.INT.fieldOf("color").forGetter(Display::color),
                Codec.INT.fieldOf("cost").forGetter(Display::cost),
                Codec.BOOL.fieldOf("requires_power").forGetter(Display::requiresPower)
        ).apply(i, Display::new));

        static Display of(ResourceLocation id, PerkDef def) {
            return new Display(id, def.name(), def.color(), def.cost(), def.requiresPower());
        }
    }

    public static final class Entity extends ConfigBlockEntity<Config> implements Prompted {
        private static final String DISPLAY = "display";
        private static final int UNSET_COLOR = 0xFFFFFF;

        private Optional<Display> synced = Optional.empty();

        public Entity(BlockPos pos, BlockState state) {
            super(PapRegistries.PERK_MACHINE_ENTITY.get(), pos, state, Config.CODEC, new Config(Optional.empty()));
        }

        /** Resolved from the datapack on the server, and from the last sync on the client. */
        public Optional<Display> display() {
            if (level == null || level.isClientSide) {
                return synced;
            }
            return config().perk().flatMap(id -> PerkDefs.get(id).map(def -> Display.of(id, def)));
        }

        public int color() {
            return display().map(Display::color).orElse(UNSET_COLOR);
        }

        /** Resends the machine to clients, after a /reload changed its perk. */
        public void refresh() {
            changed();
        }

        @Override
        public Optional<Prompt> prompt() {
            return display().filter(d -> !ClientPerks.owns(d.perk()))
                    .map(d -> new Prompt("buy " + Language.getInstance().getOrDefault(d.name()), d.cost(), d.requiresPower()));
        }

        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = super.getUpdateTag();
            display().flatMap(d -> Display.CODEC.encodeStart(NbtOps.INSTANCE, d).result()).ifPresent(d -> tag.put(DISPLAY, d));
            return tag;
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            synced = tag.contains(DISPLAY) ? Display.CODEC.parse(NbtOps.INSTANCE, tag.get(DISPLAY)).result() : Optional.empty();
        }

        @Override
        public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
            super.onDataPacket(net, packet);
            // The tint comes from this block entity, so the chunk has to be redrawn when it changes.
            if (level != null && level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            }
        }
    }

    public PerkMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.PERK_MACHINE;
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
        Optional<ResourceLocation> id = config(game.level(), pos).perk();
        Optional<PerkDef> def = id.flatMap(PerkDefs::get);
        if (def.isEmpty()) {
            Shop.refuse(player, new Refusal.Because("This machine has nothing to sell"));
            return;
        }
        Perks.buy(player, game, id.get(), def.get());
    }

    public static Config config(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Entity entity ? entity.config() : new Config(Optional.empty());
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos) {
        Optional<ResourceLocation> id = config(level, pos).perk();
        if (id.isEmpty()) {
            return Component.literal("Perk machine: no perk set. Set with /pap perk set <perk>");
        }
        return PerkDefs.get(id.get()).<Component>map(def -> Component.literal("Perk machine: ").append(Component.translatable(def.name()))
                        .append(" (" + id.get() + "), " + def.cost() + " points" + (def.requiresPower() ? ", needs power" : "")
                                + ". Set with /pap perk set <perk>"))
                .orElseGet(() -> Component.literal("Perk machine: " + id.get() + " is not a loaded perk. See /pap perk list"));
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
