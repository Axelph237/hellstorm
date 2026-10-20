package dev.candycanecazoo.packapunch.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.data.BoxPool;
import dev.candycanecazoo.packapunch.data.BoxPools;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One place the Mystery Box can be. {@code ACTIVE} marks the location the box is at this game (every location during a
 * Fire Sale); the others are empty crates. The game-wide rules (where the box is, moving, pools) live in {@link MysteryBoxes}.
 */
public final class MysteryBoxBlock extends Block implements EntityBlock, FeatureBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    /** {@code cost} overrides {@code box.cost} for this location. */
    public record Config(ResourceLocation pool, Optional<Integer> cost) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(ResourceLocation.CODEC, "pool", BoxPool.DEFAULT_ID).forGetter(Config::pool),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(Config::cost)
        ).apply(i, Config::new));

        public static final Config DEFAULT = new Config(BoxPool.DEFAULT_ID, Optional.empty());
    }

    public enum Phase { IDLE, ROLLING, OFFERING }

    public static final class Entity extends ConfigBlockEntity<Config> implements Prompted {
        private static final String STATE = "box";

        private Phase phase = Phase.IDLE;
        private long until;
        private int duration;
        private Optional<UUID> roller = Optional.empty();
        private int paid;
        private int price;
        private List<ItemStack> reel = List.of();
        private ItemStack result = ItemStack.EMPTY;
        private String resultLabel = "";
        /** Who is rolling, for skipping what they already carry. Not saved. */
        private ServerPlayer rollerPlayer;

        public Entity(BlockPos pos, BlockState state) {
            super(PapRegistries.MYSTERY_BOX_ENTITY.get(), pos, state, Config.CODEC, Config.DEFAULT);
        }

        @Override
        public void setConfig(Config config) {
            super.setConfig(config);
            if (level instanceof ServerLevel serverLevel) {
                GameManager.get(serverLevel).ifPresent(game -> MysteryBoxes.refreshPrice(game, this));
            }
        }

        @Override
        protected Optional<String> problem(Config config) {
            return BoxPools.get(config.pool()).isPresent() ? Optional.empty()
                    : Optional.of("No box pool " + config.pool() + " is loaded (data/<ns>/packapunch/box_pools/<id>.json)");
        }

        public Phase phase() {
            return phase;
        }

        public Optional<UUID> roller() {
            return roller;
        }

        public Optional<ServerPlayer> rollerPlayer() {
            return Optional.ofNullable(rollerPlayer).filter(p -> !p.isRemoved());
        }

        public int paid() {
            return paid;
        }

        /** What a roll costs here right now. Written by the server, so clients can prompt with it. */
        public int price() {
            return price;
        }

        public List<ItemStack> reel() {
            return reel;
        }

        public ItemStack result() {
            return result;
        }

        public String resultLabel() {
            return resultLabel;
        }

        public long until() {
            return until;
        }

        public int duration() {
            return duration;
        }

        public boolean active() {
            return getBlockState().getValue(ACTIVE);
        }

        void setPrice(int price) {
            if (this.price != price) {
                this.price = price;
                changed();
            }
        }

        void startRoll(ServerPlayer player, int paid, List<ItemStack> reel, int ticks) {
            phase = Phase.ROLLING;
            roller = Optional.of(player.getUUID());
            rollerPlayer = player;
            this.paid = paid;
            this.reel = List.copyOf(reel);
            result = ItemStack.EMPTY;
            resultLabel = "";
            time(ticks);
            changed();
        }

        void offer(ItemStack result, String label, int ticks) {
            phase = Phase.OFFERING;
            this.result = result;
            resultLabel = label;
            reel = List.of();
            time(ticks);
            changed();
        }

        public void idle() {
            if (phase == Phase.IDLE && roller.isEmpty()) {
                return;
            }
            phase = Phase.IDLE;
            roller = Optional.empty();
            rollerPlayer = null;
            paid = 0;
            reel = List.of();
            result = ItemStack.EMPTY;
            resultLabel = "";
            until = 0;
            duration = 0;
            changed();
        }

        private void time(int ticks) {
            duration = ticks;
            until = level.getGameTime() + ticks;
        }

        void serverTick() {
            if (phase == Phase.IDLE || level.getGameTime() < until) {
                return;
            }
            Optional<GameManager.Active> game = GameManager.get((ServerLevel) level)
                    .filter(a -> a.game().map().map().features().get(worldPosition) == FeatureKind.MYSTERY_BOX);
            if (game.isEmpty()) {
                idle();
            } else if (phase == Phase.ROLLING) {
                MysteryBoxes.finishRoll(game.get(), this);
            } else {
                idle();
                MysteryBoxes.afterUse(game.get(), this);
            }
        }

        @Override
        public Optional<Prompt> prompt() {
            if (!active()) {
                return Optional.empty();
            }
            if (phase == Phase.IDLE) {
                return Optional.of(new Prompt(net.minecraft.locale.Language.getInstance().getOrDefault("name.packapunch.mystery_box"), price, false));
            }
            boolean mine = phase == Phase.OFFERING && Viewer.get().map(p -> roller.equals(Optional.of(p.getUUID()))).orElse(false);
            return mine ? Optional.of(new Prompt(result.getHoverName().getString(), 0, false)) : Optional.empty();
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            CompoundTag box = tag.getCompound(STATE);
            phase = box.contains("phase") ? Phase.valueOf(box.getString("phase")) : Phase.IDLE;
            until = box.getLong("until");
            duration = box.getInt("duration");
            roller = box.hasUUID("roller") ? Optional.of(box.getUUID("roller")) : Optional.empty();
            paid = box.getInt("paid");
            price = box.getInt("price");
            List<ItemStack> stacks = new ArrayList<>();
            for (Tag item : box.getList("reel", Tag.TAG_COMPOUND)) {
                stacks.add(ItemStack.of((CompoundTag) item));
            }
            reel = List.copyOf(stacks);
            result = ItemStack.of(box.getCompound("result"));
            resultLabel = box.getString("label");
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            CompoundTag box = new CompoundTag();
            box.putString("phase", phase.name());
            box.putLong("until", until);
            box.putInt("duration", duration);
            roller.ifPresent(id -> box.putUUID("roller", id));
            box.putInt("paid", paid);
            box.putInt("price", price);
            ListTag stacks = new ListTag();
            reel.forEach(stack -> stacks.add(stack.save(new CompoundTag())));
            box.put("reel", stacks);
            box.put("result", result.save(new CompoundTag()));
            box.putString("label", resultLabel);
            tag.put(STATE, box);
        }

        @Override
        public AABB getRenderBoundingBox() {
            return new AABB(worldPosition).expandTowards(0, 1.5, 0);
        }
    }

    public MysteryBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != PapRegistries.MYSTERY_BOX_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, entity) -> entity.serverTick();
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.MYSTERY_BOX;
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
        entity(game.level(), pos).ifPresent(entity -> MysteryBoxes.use(player, game, entity));
    }

    public static Optional<Entity> entity(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Entity entity ? Optional.of(entity) : Optional.empty();
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos) {
        return entity(level, pos).map(MysteryBoxBlock::describe).orElse(Component.literal("Mystery Box"));
    }

    public static Component describe(Entity entity) {
        Config c = entity.config();
        return Component.literal("Mystery Box location: pool " + c.pool() + ", cost " + c.cost().map(String::valueOf).orElse("default (box.cost)")
                + ". Set with /pap box pool <id> and /pap box cost <points|default>");
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
