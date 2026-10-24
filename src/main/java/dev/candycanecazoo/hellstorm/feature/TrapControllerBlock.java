package dev.candycanecazoo.hellstorm.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.Notices;
import dev.candycanecazoo.hellstorm.game.Refusal;
import dev.candycanecazoo.hellstorm.game.Shop;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

import java.util.Collection;
import java.util.Optional;

/**
 * A trap players pay to set off. All it does is give a redstone signal for a while, like a held button; the map maker
 * builds what the signal does (dispensers, pistons, TNT, fire). Then it cools down before it can be bought again.
 */
public final class TrapControllerBlock extends HorizontalDirectionalBlock implements EntityBlock, FeatureBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Empty costs and times use the game's {@code traps.*} defaults. Without a name, players see "Trap". */
    public record Config(Optional<String> name, Optional<Integer> cost, boolean requiresPower, Optional<Integer> activeTicks,
                         Optional<Integer> cooldownTicks, int signalStrength) {
        public static final int MAX_NAME = 32;
        /** The longest active time or cooldown, an hour. */
        public static final int MAX_TICKS = 72_000;

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.STRING, "name").forGetter(Config::name),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(Config::cost),
                Codecs.strictOptional(Codec.BOOL, "requires_power", true).forGetter(Config::requiresPower),
                Codecs.strictOptional(Codec.intRange(1, MAX_TICKS), "active_ticks").forGetter(Config::activeTicks),
                Codecs.strictOptional(Codec.intRange(0, MAX_TICKS), "cooldown_ticks").forGetter(Config::cooldownTicks),
                Codecs.strictOptional(Codec.intRange(1, 15), "signal_strength", 15).forGetter(Config::signalStrength)
        ).apply(i, Config::new));

        public static final Config DEFAULT = new Config(Optional.empty(), Optional.empty(), true, Optional.empty(), Optional.empty(), 15);

        public String label() {
            return name.orElse("Trap");
        }

        public int cost(GameSettings.Traps defaults) {
            return cost.orElse(defaults.defaultCost());
        }

        public int activeTicks(GameSettings.Traps defaults) {
            return activeTicks.orElse(defaults.defaultActiveTicks());
        }

        public int cooldownTicks(GameSettings.Traps defaults) {
            return cooldownTicks.orElse(defaults.defaultCooldownTicks());
        }

        public Config withName(Optional<String> name) {
            return new Config(name, cost, requiresPower, activeTicks, cooldownTicks, signalStrength);
        }

        public Config withCost(Optional<Integer> cost) {
            return new Config(name, cost, requiresPower, activeTicks, cooldownTicks, signalStrength);
        }

        public Config withPower(boolean requiresPower) {
            return new Config(name, cost, requiresPower, activeTicks, cooldownTicks, signalStrength);
        }

        public Config withActiveTicks(Optional<Integer> activeTicks) {
            return new Config(name, cost, requiresPower, activeTicks, cooldownTicks, signalStrength);
        }

        public Config withCooldownTicks(Optional<Integer> cooldownTicks) {
            return new Config(name, cost, requiresPower, activeTicks, cooldownTicks, signalStrength);
        }

        public Config withSignal(int signalStrength) {
            return new Config(name, cost, requiresPower, activeTicks, cooldownTicks, signalStrength);
        }
    }

    public static final class Entity extends ConfigBlockEntity<Config> implements Prompted {
        private static final String CYCLE = "cycle";
        private static final String LOOK = "look";
        /** The block the trap is drawn as, for the client's model. */
        public static final ModelProperty<BlockState> LOOK_PROPERTY = new ModelProperty<>();

        private TrapCycle cycle = TrapCycle.READY;
        /** The trap's cost as the running game sees it, synced for the prompt. */
        private int shownCost;
        /** What the trap is drawn as: a map maker's choice, so it blends into the wall. */
        private Optional<BlockState> look = Optional.empty();

        public Entity(BlockPos pos, BlockState state) {
            super(PapRegistries.TRAP_CONTROLLER_ENTITY.get(), pos, state, Config.CODEC, Config.DEFAULT);
        }

        public TrapCycle cycle() {
            return cycle;
        }

        public Optional<BlockState> look() {
            return look;
        }

        public void setLook(Optional<BlockState> look) {
            this.look = look;
            changed();
        }

        @Override
        public ModelData getModelData() {
            return look.map(state -> ModelData.builder().with(LOOK_PROPERTY, state).build()).orElse(ModelData.EMPTY);
        }

        @Override
        protected Optional<String> problem(Config config) {
            return config.name().filter(n -> n.isBlank() || n.length() > Config.MAX_NAME)
                    .map(n -> "A trap's name must be 1 to " + Config.MAX_NAME + " characters");
        }

        /** No prompt while it runs or cools down: there's nothing to buy until it's ready again. */
        @Override
        public Optional<Prompt> prompt() {
            return cycle.phase() == TrapCycle.Phase.READY ? Optional.of(new Prompt(config().label(), shownCost, config().requiresPower())) : Optional.empty();
        }

        /** Gives the signal now, whoever paid. */
        public void activate(ServerLevel level) {
            setCycle(level, TrapCycle.activated(level.getGameTime(), config().activeTicks(defaults(level))));
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0f, 0.8f);
        }

        void reset(ServerLevel level) {
            setCycle(level, TrapCycle.READY);
        }

        void tick(ServerLevel level) {
            GameSettings.Traps defaults = defaults(level);
            int cost = config().cost(defaults);
            if (cost != shownCost) {
                shownCost = cost;
                changed();
            }
            TrapCycle next = cycle.at(level.getGameTime(), config().cooldownTicks(defaults));
            if (!next.equals(cycle)) {
                if (cycle.powered() && !next.powered()) {
                    level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 1.0f, 0.6f);
                }
                setCycle(level, next);
            }
        }

        private void setCycle(ServerLevel level, TrapCycle next) {
            cycle = next;
            BlockState state = getBlockState();
            if (state.getValue(POWERED) != next.powered()) {
                BlockState updated = state.setValue(POWERED, next.powered());
                level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
                updatePowered(level, worldPosition, updated.getBlock());
            }
            changed();
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            CompoundTag saved = tag.getCompound(CYCLE);
            TrapCycle.Phase phase = TrapCycle.Phase.READY;
            for (TrapCycle.Phase p : TrapCycle.Phase.values()) {
                if (p.getSerializedName().equals(saved.getString("phase"))) {
                    phase = p;
                }
            }
            cycle = new TrapCycle(phase, saved.getLong("until"));
            shownCost = tag.getInt("shown_cost");
            Optional<BlockState> before = look;
            look = tag.contains(LOOK) ? Optional.of(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(LOOK)))
                    .filter(state -> !state.isAir()) : Optional.empty();
            if (level != null && level.isClientSide && !look.equals(before)) {
                // The chunk is drawn from model data, so it has to be fetched again and the block redrawn.
                requestModelDataUpdate();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            CompoundTag saved = new CompoundTag();
            saved.putString("phase", cycle.phase().getSerializedName());
            saved.putLong("until", cycle.until());
            tag.put(CYCLE, saved);
            tag.putInt("shown_cost", shownCost);
            look.ifPresent(state -> tag.put(LOOK, NbtUtils.writeBlockState(state)));
        }
    }

    public TrapControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    static GameSettings.Traps defaults(ServerLevel level) {
        return GameManager.get(level).map(a -> a.game().settings().traps()).orElseGet(() -> GameManager.settings().traps());
    }

    /** Wakes every block the trap powers, and the blocks those power in turn, as a button does when it clicks. */
    private static void updatePowered(Level level, BlockPos pos, Block block) {
        level.updateNeighborsAt(pos, block);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), block);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
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
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != PapRegistries.TRAP_CONTROLLER_ENTITY.get()
                ? null
                : (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, trap) -> trap.tick((ServerLevel) l);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && level.getBlockEntity(pos) instanceof Entity trap ? trap.config().signalStrength() : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.TRAP;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND && !restyle(serverPlayer, pos, hit)) {
            FeatureBlock.use(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Outside a game, in creative: using the trap with a block in hand draws the trap as that block, and sneaking with an empty hand puts its own look back. Returns whether the click was taken for that.
     */
    static boolean restyle(ServerPlayer player, BlockPos pos, BlockHitResult hit) {
        if (!player.isCreative() || GameManager.forPlayer(player.getUUID()).isPresent()
                || !(player.level().getBlockEntity(pos) instanceof Entity trap)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() && player.isShiftKeyDown() && trap.look().isPresent()) {
            trap.setLook(Optional.empty());
            Notices.show(player, Component.literal("The trap looks like itself again"));
            return true;
        }
        if (!(held.getItem() instanceof BlockItem item)) {
            return false;
        }
        BlockState placed = item.getBlock().getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit));
        BlockState look = placed == null ? item.getBlock().defaultBlockState() : placed;
        if (!canLookLike(look, player.level(), pos)) {
            Notices.show(player, Component.literal(look.getBlock().getName().getString() + " can't be a trap's look: only full, solid blocks can")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        trap.setLook(Optional.of(look));
        Notices.show(player, Component.literal("The trap now looks like " + look.getBlock().getName().getString()
                + ". Sneak and use it with an empty hand to undo"));
        player.level().playSound(null, pos, look.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
        return true;
    }

    /**
     * Only full, solid blocks, so the trap still hides what's behind it, and nothing drawn by its own renderer or another
     * machine.
     */
    public static boolean canLookLike(BlockState look, BlockGetter level, BlockPos pos) {
        return !(look.getBlock() instanceof FeatureBlock) && look.getRenderShape() == RenderShape.MODEL && look.isSolidRender(level, pos);
    }

    @Override
    public void buy(ServerPlayer player, GameManager.Active game, BlockPos pos, BlockState state) {
        if (!(game.level().getBlockEntity(pos) instanceof Entity trap)) {
            return;
        }
        Config config = trap.config();
        GameSettings.Traps defaults = game.game().settings().traps();
        Shop.buy(player, game, new Shop.Sale() {
            @Override
            public int cost() {
                return config.cost(defaults);
            }

            @Override
            public boolean needsPower() {
                return config.requiresPower();
            }

            @Override
            public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                TrapCycle cycle = trap.cycle();
                if (cycle.phase() == TrapCycle.Phase.ACTIVE) {
                    return Optional.of(new Refusal.Because(config.label() + " is already running"));
                }
                if (cycle.phase() == TrapCycle.Phase.COOLDOWN) {
                    return Optional.of(new Refusal.Because(config.label() + " is cooling down (" + cycle.secondsLeft(g.level().getGameTime()) + "s)"));
                }
                return Optional.empty();
            }

            @Override
            public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                trap.activate(g.level());
                g.game().events().emit(new GameEvent.TrapActivated(p.getUUID(), pos.immutable()));
                return Optional.empty();
            }
        });
    }

    /** Puts every trap back to ready with its signal off. */
    public static void resetAll(ServerLevel level, Collection<BlockPos> traps) {
        for (BlockPos pos : traps) {
            if (level.getBlockEntity(pos) instanceof Entity trap) {
                trap.reset(level);
            }
        }
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
        if (!state.is(newState.getBlock()) && state.getValue(POWERED)) {
            updatePowered(level, pos, this);
        }
    }
}
