package dev.candycanecazoo.hellstorm.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.Refusal;
import dev.candycanecazoo.hellstorm.game.Shop;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.pap.PunchDefs;
import dev.candycanecazoo.hellstorm.pap.PunchOutcome;
import dev.candycanecazoo.hellstorm.pap.PunchResolver;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tacz.PapGunStats;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Upgrades the item a player is holding. Two blocks long, like a bed: {@code FACING} points from the foot, which holds
 * the machine's block entity and is the one the map indexes, to the head. The machine lays the item across its top, a
 * spark catches, fire creeps along it for {@code pap.process_ticks}, and then the upgraded item lies there for that
 * player alone for {@code pap.collect_ticks}. Uncollected results are lost, as in CoD. A refused item is never taken
 * and costs nothing.
 */
public final class PapMachineBlock extends HorizontalDirectionalBlock implements EntityBlock, FeatureBlock {
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    /** The machine's name, from the lang file so a resource pack can rename it. */
    public static final String NAME = "name.hellstorm.pack_a_punch";
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);
    /** Where the item lies, above the machine's floor, and how far it reaches either side of the middle. */
    public static final double TOP = 12.0 / 16;
    public static final double ITEM_HALF_LENGTH = 0.85;

    /** {@code cost} replaces {@code pap.default_cost} for this machine. A {@code PunchDef}'s own cost still wins. */
    public record Config(Optional<Integer> cost) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(Config::cost)
        ).apply(i, Config::new));
    }

    /**
     * An item in the machine. {@code until} is the game time it finishes processing, or, once ready, expires; {@code started}
     * is when it went in, so clients can draw how far the fire has spread.
     */
    record Job(UUID owner, ItemStack input, ItemStack result, boolean ready, long started, long until, int collectTicks) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", owner);
            tag.put("input", input.save(new CompoundTag()));
            tag.put("result", result.save(new CompoundTag()));
            tag.putBoolean("ready", ready);
            tag.putLong("started", started);
            tag.putLong("until", until);
            tag.putInt("collect_ticks", collectTicks);
            return tag;
        }

        static Job load(CompoundTag tag) {
            return new Job(tag.getUUID("owner"), ItemStack.of(tag.getCompound("input")), ItemStack.of(tag.getCompound("result")),
                    tag.getBoolean("ready"), tag.getLong("started"), tag.getLong("until"), tag.getInt("collect_ticks"));
        }
    }

    public static final class Entity extends ConfigBlockEntity<Config> implements Prompted {
        private Optional<Job> job = Optional.empty();
        /** The machine's cost as the running game sees it, synced for the prompt. */
        private int shownCost;

        public Entity(BlockPos pos, BlockState state) {
            super(PapRegistries.PAP_MACHINE_ENTITY.get(), pos, state, Config.CODEC, new Config(Optional.empty()));
        }

        /** What to draw in the machine: the item being worked on, then the result. */
        public ItemStack displayed() {
            return job.map(j -> j.ready() ? j.result() : j.input()).orElse(ItemStack.EMPTY);
        }

        public boolean ready() {
            return job.map(Job::ready).orElse(false);
        }

        public boolean empty() {
            return job.isEmpty();
        }

        @Override
        public Optional<Prompt> prompt() {
            if (job.isEmpty()) {
                return Optional.of(new Prompt(net.minecraft.locale.Language.getInstance().getOrDefault(NAME), shownCost, true));
            }
            return job.get().ready() ? Optional.of(new Prompt(job.get().result().getHoverName().getString(), 0, false)) : Optional.empty();
        }

        void use(ServerPlayer player, GameManager.Active game) {
            if (job.isPresent() && job.get().ready() && job.get().owner().equals(player.getUUID())) {
                Shop.buy(player, game, collect());
                return;
            }
            GameSettings settings = game.game().settings();
            ItemStack held = player.getMainHandItem();
            PunchOutcome outcome = PunchResolver.resolve(held.getTag(), TaczBridge.inspect(held).isPresent(), PunchDefs.all(),
                    config().cost().orElse(settings.pap().defaultCost()), settings.pap().maxTier());
            Optional<ItemStack> result = held.isEmpty() ? Optional.empty() : upgraded(held.copyWithCount(1), outcome, settings);
            Shop.buy(player, game, new Shop.Sale() {
                @Override
                public int cost() {
                    return outcome.cost();
                }

                @Override
                public boolean needsPower() {
                    return true;
                }

                @Override
                public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                    if (job.isPresent()) {
                        return Optional.of(new Refusal.Because(job.get().owner().equals(p.getUUID())
                                ? "Your item is still being upgraded" : "The machine is busy"));
                    }
                    if (held.isEmpty()) {
                        return Optional.of(new Refusal.Because("Hold the item you want to upgrade"));
                    }
                    if (outcome instanceof PunchOutcome.Rejected rejected) {
                        return Optional.of(new Refusal.Translated(rejected.reason().key(), List.of(NAME)));
                    }
                    return result.isEmpty() ? Optional.of(new Refusal.Because("This item's upgrade isn't installed")) : Optional.empty();
                }

                @Override
                public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                    ItemStack input = held.split(1);
                    long now = g.level().getGameTime();
                    job = Optional.of(new Job(p.getUUID(), input, result.get(), false, now, now + settings.pap().processTicks(),
                            settings.pap().collectTicks()));
                    changed();
                    Vec3 spark = along(-1);
                    g.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, spark.x, spark.y, spark.z, 12, 0.05, 0.05, 0.05, 0.2);
                    g.level().playSound(null, worldPosition, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
                    g.level().playSound(null, worldPosition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    g.game().events().emit(new GameEvent.PapUsed(p.getUUID(), describe(input)));
                    return Optional.empty();
                }
            });
        }

        private Shop.Sale collect() {
            return new Shop.Sale() {
                @Override
                public int cost() {
                    return 0;
                }

                @Override
                public boolean needsPower() {
                    return false;
                }

                @Override
                public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                    ItemStack give = job.get().result().copy();
                    if (p.getMainHandItem().isEmpty()) {
                        p.setItemInHand(InteractionHand.MAIN_HAND, give);
                    } else if (!p.getInventory().add(give)) {
                        return Optional.of(new Refusal.Because("Your inventory is full"));
                    }
                    job = Optional.empty();
                    changed();
                    return Optional.empty();
                }
            };
        }

        /** The finished item, or empty when it can't be made (a result item or gun that isn't installed). */
        private static Optional<ItemStack> upgraded(ItemStack input, PunchOutcome outcome, GameSettings settings) {
            if (outcome instanceof PunchOutcome.UpgradeTacz) {
                return Optional.of(PapGunStats.upgrade(input, settings.pap()));
            }
            if (outcome instanceof PunchOutcome.Replace replace) {
                return replace.def().result().build(settings.players().reserveMagazines()).map(stack -> {
                    replace.def().name().ifPresent(key -> stack.setHoverName(Component.translatable(key)));
                    PapGunStats.carryOver(input, stack, replace.def().carryOver().ammo(), replace.def().carryOver().attachments());
                    return stack;
                });
            }
            return Optional.empty();
        }

        /** The gun id for TacZ guns, the item id otherwise. */
        private static String describe(ItemStack stack) {
            return TaczBridge.inspect(stack).map(info -> info.gunId().toString())
                    .orElseGet(() -> String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
        }

        void tick(ServerLevel level) {
            int cost = config().cost().orElseGet(() -> GameManager.get(level)
                    .map(a -> a.game().settings().pap().defaultCost())
                    .orElseGet(() -> ServerConfig.GAME.get().pap().defaultCost()));
            if (cost != shownCost) {
                shownCost = cost;
                changed();
            }
            if (job.isEmpty() || level.getGameTime() < job.get().until()) {
                return;
            }
            Job current = job.get();
            if (!current.ready()) {
                job = Optional.of(new Job(current.owner(), current.input(), current.result(), true, current.started(),
                        level.getGameTime() + current.collectTicks(), current.collectTicks()));
                Vec3 middle = along(0);
                level.sendParticles(ParticleTypes.FLAME, middle.x, middle.y, middle.z, 40, ITEM_HALF_LENGTH / 2, 0.08, ITEM_HALF_LENGTH / 2, 0.04);
                level.sendParticles(ParticleTypes.LAVA, middle.x, middle.y, middle.z, 6, ITEM_HALF_LENGTH / 2, 0.05, ITEM_HALF_LENGTH / 2, 0);
                level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0f, 0.9f);
                level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.2f);
            } else {
                job = Optional.empty();
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(current.owner());
                if (owner != null) {
                    dev.candycanecazoo.hellstorm.game.Notices.show(owner, Component.literal("Your upgraded item was lost").withStyle(ChatFormatting.RED));
                }
            }
            changed();
        }

        /**
         * A point on the line the item lies along, from {@code -1} (its end over the foot) through {@code 0} (the seam
         * between the halves) to {@code 1} (its end over the head).
         */
        public Vec3 along(double t) {
            Direction facing = getBlockState().getValue(FACING);
            return Vec3.atLowerCornerOf(worldPosition).add(0.5, TOP + 0.08, 0.5)
                    .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5 + t * ITEM_HALF_LENGTH));
        }

        /** How far the fire has spread along the item, from 0 to 1. 1 once it's done. */
        public float progress(long now) {
            if (job.isEmpty() || job.get().ready()) {
                return 1;
            }
            Job j = job.get();
            return j.until() <= j.started() ? 1 : Mth.clamp((float) (now - j.started()) / (j.until() - j.started()), 0, 1);
        }

        /** Fire creeping along the item from the spark at the foot end, flames behind the front and sparks at it. */
        void clientTick(Level level) {
            if (job.isEmpty() || job.get().ready()) {
                return;
            }
            RandomSource random = level.random;
            double front = -1 + 2 * progress(level.getGameTime());
            Vec3 at = along(front);
            level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 0, 0.01, 0);
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
            }
            for (int i = 0; i < 2; i++) {
                Vec3 burning = along(-1 + random.nextDouble() * (front + 1)).add((random.nextDouble() - 0.5) * 0.15, 0, (random.nextDouble() - 0.5) * 0.15);
                level.addParticle(ParticleTypes.FLAME, burning.x, burning.y, burning.z, 0, 0.015, 0);
            }
        }

        void clear() {
            if (job.isPresent()) {
                job = Optional.empty();
                changed();
            }
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            job = tag.contains("job") ? Optional.of(Job.load(tag.getCompound("job"))) : Optional.empty();
            shownCost = tag.getInt("shown_cost");
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            job.ifPresent(j -> tag.put("job", j.save()));
            tag.putInt("shown_cost", shownCost);
        }
    }

    public PapMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The head goes one block the way the player faces, so there has to be room for it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos head = context.getClickedPos().relative(facing);
        Level level = context.getLevel();
        return level.getBlockState(head).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(head)
                ? defaultBlockState().setValue(FACING, facing) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
        }
    }

    /** Places a whole machine, foot at {@code foot}, for code that doesn't go through a player placing the item. */
    public static void place(Level level, BlockPos foot, Direction facing) {
        BlockState state = PapRegistries.PAP_MACHINE.get().defaultBlockState().setValue(FACING, facing);
        level.setBlock(foot, state, Block.UPDATE_ALL);
        level.setBlock(foot.relative(facing), state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
    }

    private static Direction towardOtherHalf(BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
    }

    /** One half without the other is gone too, as with a bed. */
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
        return state.getValue(PART) == BedPart.HEAD ? pos.relative(state.getValue(FACING).getOpposite()) : pos;
    }

    @Override
    public java.util.List<BlockPos> blocks(BlockState state, BlockPos pos) {
        BlockPos foot = mainPos(state, pos);
        return java.util.List.of(foot, foot.relative(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? new Entity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != PapRegistries.PAP_MACHINE_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
                ? (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, machine) -> machine.clientTick(l)
                : (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, machine) -> machine.tick((ServerLevel) l);
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.PAP_MACHINE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            FeatureBlock.use(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void buy(ServerPlayer player, GameManager.Active game, BlockPos pos, BlockState state) {
        if (game.level().getBlockEntity(pos) instanceof Entity machine) {
            machine.use(player, game);
        }
    }

    /** Empties every machine. An item left in one is gone. */
    public static void resetAll(ServerLevel level, Collection<BlockPos> machines) {
        for (BlockPos pos : machines) {
            if (level.getBlockEntity(pos) instanceof Entity machine) {
                machine.clear();
            }
        }
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos) {
        Optional<Integer> cost = level.getBlockEntity(pos) instanceof Entity machine ? machine.config().cost() : Optional.empty();
        return Component.literal("Pack-a-Punch machine: " + cost.map(c -> c + " points").orElse("the default cost (pap.default_cost)")
                + ". Set with /hs pap cost <points|default>");
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
