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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Upgrades the item a player is holding. Two blocks long ({@link TwoBlockMachine}). The machine lays the item across its top, a
 * spark catches, fire creeps along it for {@code pap.process_ticks}, and then the upgraded item lies there for that
 * player alone for {@code pap.collect_ticks}. Uncollected results are lost, as in CoD. A refused item is never taken
 * and costs nothing.
 */
public final class PapMachineBlock extends TwoBlockMachine {
    /** The machine's name, from the lang file so a resource pack can rename it. */
    public static final String NAME = "name.hellstorm.pack_a_punch";
    /** The GeckoLib model and texture, under {@code geo/block/} and {@code textures/block/}. */
    public static final String MODEL = "hellfire_upgrader";
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);
    /** Where the item lies, above the machine's floor, and how far it reaches either side of the middle. */
    public static final double TOP = 13.0 / 16;
    public static final double ITEM_HALF_LENGTH = 0.85;
    /** The item lies this far to one side of the middle, clear of the skull and the candles on the other side. */
    public static final double SIDE_OFFSET = 3.0 / 16;
    /**
     * The three candles' flames, in the model's pixels: from the foot's corner, with the head toward +z. Half a pixel
     * above each wick's top, as GeckoLib draws the turned candles (measured from the rendered model).
     */
    private static final double[][] WICKS = {{4.58, 17.5, 29.87}, {1.88, 19.5, 29.02}, {4.08, 20.5, 26.75}};
    /** The ding comes this long after the fire goes out. */
    private static final int DING_DELAY = 10;

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

    public static final class Entity extends ConfigBlockEntity<Config> implements Prompted, GeoBlockEntity {
        private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
        private Optional<Job> job = Optional.empty();
        /** The machine's cost as the running game sees it, synced for the prompt. */
        private int shownCost;

        public Entity(BlockPos pos, BlockState state) {
            super(PapRegistries.PAP_MACHINE_ENTITY.get(), pos, state, Config.CODEC, new Config(Optional.empty()));
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return animations;
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
                    g.level().playSound(null, worldPosition, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
                    g.level().playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0f, 0.9f);
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
            if (job.isPresent() && job.get().ready() && level.getGameTime() == job.get().until() - job.get().collectTicks() + DING_DELAY) {
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6f, 1.5f);
            }
            if (job.isEmpty() || level.getGameTime() < job.get().until()) {
                return;
            }
            Job current = job.get();
            if (!current.ready()) {
                job = Optional.of(new Job(current.owner(), current.input(), current.result(), true, current.started(),
                        level.getGameTime() + current.collectTicks(), current.collectTicks()));
                Vec3 middle = along(0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, middle.x, middle.y + 0.1, middle.z, 30, ITEM_HALF_LENGTH / 2, 0.1, 0.15, 0.35);
                level.sendParticles(ParticleTypes.SMOKE, middle.x, middle.y + 0.1, middle.z, 12, ITEM_HALF_LENGTH / 2, 0.05, 0.12, 0.02);
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
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
                    .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5 + t * ITEM_HALF_LENGTH))
                    .add(Vec3.atLowerCornerOf(side(facing).getNormal()).scale(SIDE_OFFSET));
        }

        /** Ticks the item has been burning, or -1 when nothing is burning. */
        public long burning(long now) {
            return job.isPresent() && !job.get().ready() ? Math.max(0, now - job.get().started()) : -1;
        }

        @Override
        public AABB getRenderBoundingBox() {
            Direction facing = getBlockState().getValue(FACING);
            return new AABB(worldPosition).minmax(new AABB(worldPosition.relative(facing))).expandTowards(0, 1, 0);
        }

        /** How far the fire has spread along the item, from 0 to 1. 1 once it's done. */
        public float progress(long now) {
            if (job.isEmpty() || job.get().ready()) {
                return 1;
            }
            Job j = job.get();
            return j.until() <= j.started() ? 1 : Mth.clamp((float) (now - j.started()) / (j.until() - j.started()), 0, 1);
        }

        /** While the item burns, embers and smoke rise off it. The flames themselves are drawn by the renderer. */
        void clientTick(Level level) {
            if (burning(level.getGameTime()) < 0) {
                return;
            }
            RandomSource random = level.random;
            Vec3 at = along(random.nextDouble() * 2 - 1).add(0, 0.25 + random.nextDouble() * 0.2, 0);
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.FLAME, at.x, at.y, at.z, 0, 0.02, 0);
            }
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.SMOKE, at.x, at.y + 0.2, at.z, 0, 0.03, 0);
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
    }

    /** Places a whole machine, foot at {@code foot}, for code that doesn't go through a player placing the item. */
    public static void place(Level level, BlockPos foot, Direction facing) {
        place(level, foot, PapRegistries.PAP_MACHINE.get().defaultBlockState().setValue(FACING, facing));
    }

    /** The model's +x side, which the item shifts toward: the table's long side without the skull and candles. */
    static Direction side(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The candles burn like vanilla candles: a small flame at each wick, now and then smoke and a crackle. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != BedPart.FOOT) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (double[] wick : WICKS) {
            Vec3 at = modelPoint(pos, facing, wick[0], wick[1], wick[2]);
            float roll = random.nextFloat();
            if (roll < 0.3f) {
                level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0, 0);
                if (roll < 0.17f) {
                    level.playLocalSound(at.x, at.y, at.z, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS, 1.0f + random.nextFloat(), random.nextFloat() * 0.7f + 0.3f, false);
                }
            }
            level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 0, 0, 0);
        }
    }

    /**
     * A point of the model in the world: {@code x, y, z} in pixels from the foot's corner as the model is drawn (head
     * toward +z), turned the way the blockstate turns the model for {@code facing}.
     */
    static Vec3 modelPoint(BlockPos foot, Direction facing, double x, double y, double z) {
        double dx = x / 16 - 0.5;
        double dz = z / 16 - 0.5;
        double rx;
        double rz;
        switch (facing) {
            case WEST -> { rx = -dz; rz = dx; }
            case NORTH -> { rx = -dx; rz = -dz; }
            case EAST -> { rx = dz; rz = -dx; }
            default -> { rx = dx; rz = dz; }
        }
        return Vec3.atLowerCornerOf(foot).add(0.5 + rx, y / 16, 0.5 + rz);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockEntity newFootEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
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
        return Component.translatable(NAME).append(": " + cost.map(c -> c + " points").orElse("the default cost (pap.default_cost)")
                + ". Set with /hs upgrader cost <points|default>");
    }
}
