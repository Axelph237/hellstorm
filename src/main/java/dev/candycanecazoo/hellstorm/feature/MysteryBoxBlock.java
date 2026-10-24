package dev.candycanecazoo.hellstorm.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.data.BoxPool;
import dev.candycanecazoo.hellstorm.data.BoxPools;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * One place the Mystery Crate can be, two blocks long ({@link TwoBlockMachine}). {@link #BOX} says whether the box is here
 * this game; during a Fire Sale it is at every location. The game-wide rules (where the box is, moving, pools) live in
 * {@link MysteryBoxes}. A roll opens the lid, guns rise out of the crate flicking past until one settles, and the lid
 * closes again once it is taken or puffs away.
 */
public final class MysteryBoxBlock extends TwoBlockMachine {
    public static final EnumProperty<Presence> BOX = EnumProperty.create("box", Presence.class);
    /** The GeckoLib model, texture and animations, under {@code geo/block/}, {@code textures/block/} and {@code animations/block/}. */
    public static final String MODEL = "mystery_crate";
    /** The model is made with its body's corner at the origin. In its pixels, this centers it on the two blocks. */
    public static final Vec3 MODEL_OFFSET = new Vec3(-7, 0, -7);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);
    /** How long the lid takes to open, and to close. The client fits the model's animation to it. */
    public static final int LID_TICKS = 20;
    /** How long vanilla's chest sounds last at normal speed; the close sounds are all about the same. */
    private static final float CHEST_OPEN_SECONDS = 0.515f;
    private static final float CHEST_CLOSE_SECONDS = 0.625f;
    /** The middle of a gun as it rises: from inside the crate, under the portal, to where it hangs. In blocks above the floor. */
    public static final double GUN_LOW = 0.45;
    public static final double GUN_HIGH = 1.35;
    /** How many guns flick past on the way up. */
    public static final int FLICKS = 30;
    /** How often a few flames flicker up on the crate. */
    private static final int SMOULDER_TICKS = 100;

    /** Whether a location has the box. {@code STORED} is every location outside a game, shown closed for map makers. */
    public enum Presence implements StringRepresentable {
        STORED, HERE, AWAY;

        /** {@code AWAY} locations are gone: not drawn, and nothing to bump into. */
        public boolean visible() {
            return this != AWAY;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** {@code cost} overrides {@code box.cost} for this location. */
    public record Config(ResourceLocation pool, Optional<Integer> cost) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(ResourceLocation.CODEC, "pool", BoxPool.DEFAULT_ID).forGetter(Config::pool),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(Config::cost)
        ).apply(i, Config::new));

        public static final Config DEFAULT = new Config(BoxPool.DEFAULT_ID, Optional.empty());
    }

    /** {@code ROLLING} starts with {@link #LID_TICKS} of the lid opening, then the guns rise. */
    public enum Phase { IDLE, ROLLING, OFFERING, CLOSING }

    public static final class Entity extends ConfigBlockEntity<Config> implements Prompted, GeoBlockEntity {
        private static final String STATE = "box";
        private static final String ELAPSED = "elapsed";
        public static final String OPEN = "box_open";
        public static final String CLOSE = "box_close";
        private static final RawAnimation OPEN_LID = RawAnimation.begin().thenPlayAndHold(OPEN);
        private static final RawAnimation CLOSE_LID = RawAnimation.begin().thenPlayAndHold(CLOSE);
        private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);

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
        /** The client's clock for the guns rising. Not saved. */
        private final BoxSpin spin = new BoxSpin();
        /** The flick the client last dinged for. */
        private int dinged = -1;

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
                    : Optional.of("No box pool " + config.pool() + " is loaded (data/<ns>/hellstorm/box_pools/<id>.json)");
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
            return getBlockState().getValue(BOX) == Presence.HERE;
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
            lidSound(SoundEvents.CHEST_OPEN, CHEST_OPEN_SECONDS);
        }

        void offer(ItemStack result, String label, int ticks) {
            phase = Phase.OFFERING;
            this.result = result;
            resultLabel = label;
            reel = List.of();
            time(ticks);
            changed();
        }

        /** The lid closes, with nothing in the crate. */
        void close() {
            phase = Phase.CLOSING;
            roller = Optional.empty();
            rollerPlayer = null;
            paid = 0;
            reel = List.of();
            result = ItemStack.EMPTY;
            resultLabel = "";
            time(LID_TICKS);
            changed();
            lidSound(SoundEvents.CHEST_CLOSE, CHEST_CLOSE_SECONDS);
        }

        /**
         * {@code sound}, {@code seconds} long, slowed to last as long as the lid moves. Pitch is playback speed, and the
         * client won't go below half speed, so a lid slower than twice the sound's length outlasts it.
         */
        private void lidSound(SoundEvent sound, float seconds) {
            Vec3 at = gunAt(0.8);
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0f, seconds / (LID_TICKS / 20f));
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
                return;
            }
            switch (phase) {
                case ROLLING -> MysteryBoxes.finishRoll(game.get(), this);
                case OFFERING -> {
                    vanishGun();
                    close();
                }
                default -> {
                    idle();
                    MysteryBoxes.afterUse(game.get(), this);
                }
            }
        }

        /**
         * On the client, how far the guns have risen {@code partialTick} past its last tick ({@link BoxSpin}). Empty unless
         * they're rising: only while rolling, once the lid is open.
         */
        public Optional<Float> rise(float partialTick) {
            return phase == Phase.ROLLING && !reel.isEmpty() ? spin.rise(partialTick) : Optional.empty();
        }

        /** Which gun of the reel shows at {@code rise}. */
        public ItemStack flick(float rise) {
            return reel.get(BoxSpin.flick(rise) % reel.size());
        }

        void clientTick() {
            spin.tick();
            ding();
            smoulder();
        }

        /** A ding each time the rising gun changes, like a prize wheel's clicker. */
        private void ding() {
            int flick = rise(0).map(BoxSpin::flick).orElse(-1);
            if (flick > dinged) {
                Vec3 at = gunAt(GUN_HIGH);
                level.playLocalSound(at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.35f, 1.5f, false);
            }
            dinged = flick;
        }

        /** Every {@link #SMOULDER_TICKS}, one or two small flames on the crate's outside, as if it were still burning. */
        private void smoulder() {
            if (!getBlockState().getValue(BOX).visible() || (level.getGameTime() + worldPosition.hashCode()) % SMOULDER_TICKS != 0) {
                return;
            }
            RandomSource random = level.random;
            for (int i = 1 + random.nextInt(2); i > 0; i--) {
                Vec3 at = surfacePoint(random);
                level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 0, 0.004, 0);
            }
        }

        /**
         * A random point on the crate's outside, weighted by area: the lid's top while it's shut, the long sides, and the
         * ends. Measured from the model in its pixels, x across from the foot's corner, z toward the head.
         */
        private Vec3 surfacePoint(RandomSource random) {
            double top = phase == Phase.IDLE ? 17 * 33 : 0;
            double sides = 2 * 30 * 9.5;
            double ends = 2 * 14 * 9.5;
            double pick = random.nextDouble() * (top + sides + ends);
            double x;
            double y;
            double z;
            if (pick < top) {
                x = -0.5 + random.nextDouble() * 17;
                y = 13.9;
                z = -0.5 + random.nextDouble() * 33;
            } else if (pick < top + sides) {
                x = random.nextBoolean() ? 0.5 : 15.5;
                y = 2 + random.nextDouble() * 9.5;
                z = 1 + random.nextDouble() * 30;
            } else {
                x = 1 + random.nextDouble() * 14;
                y = 2 + random.nextDouble() * 9.5;
                z = random.nextBoolean() ? 0.5 : 31.5;
            }
            Direction along = along(getBlockState());
            Direction across = along.getCounterClockWise();
            double a = x / 16 - 0.5;
            double b = z / 16 - 0.5;
            return Vec3.atBottomCenterOf(worldPosition).add(a * across.getStepX() + b * along.getStepX(), y / 16,
                    a * across.getStepZ() + b * along.getStepZ());
        }

        /** Where a gun hangs over the crate: over the seam between the halves, {@code height} blocks up. */
        public Vec3 gunAt(double height) {
            Direction along = along(getBlockState());
            return Vec3.atBottomCenterOf(worldPosition).add(along.getStepX() * 0.5, height, along.getStepZ() * 0.5);
        }

        /**
         * The gun hanging over the crate bursts into smoke, to the ender dragon's roar. The dragon itself roars at volume 2.5,
         * which carries 40 blocks. At 1 or under a sound carries vanilla's 16 blocks and fades over them, so 0.6 keeps it to the
         * crate's surroundings.
         */
        void vanishGun() {
            Vec3 at = gunAt(GUN_HIGH);
            puff(at, 0.5, 0.15, 25);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 0.6f, 1.0f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.0f);
        }

        /** The whole crate bursts into smoke. */
        void vanish() {
            puff(gunAt(0.4), 1, 0.4, 70);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.2f);
        }

        /** Smoke spread {@code along} the crate's length and {@code across} its width and height. */
        private void puff(Vec3 at, double along, double across, int count) {
            if (level instanceof ServerLevel serverLevel) {
                boolean x = along(getBlockState()).getAxis() == Direction.Axis.X;
                double dx = x ? along / 2 : across / 2;
                double dz = x ? across / 2 : along / 2;
                serverLevel.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, count, dx, across / 2, dz, 0.02);
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, count / 5, dx, across / 2, dz, 0.01);
            }
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "lid", 0, state -> switch (phase) {
                case ROLLING, OFFERING -> state.setAndContinue(OPEN_LID);
                case CLOSING -> state.setAndContinue(CLOSE_LID);
                case IDLE -> {
                    // So the next roll opens from the start, even though the controller last played the same animation.
                    state.getController().forceAnimationReset();
                    yield PlayState.STOP;
                }
            }));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return animations;
        }

        @Override
        public Optional<Prompt> prompt() {
            if (!active()) {
                return Optional.empty();
            }
            if (phase == Phase.IDLE) {
                return Optional.of(new Prompt(net.minecraft.locale.Language.getInstance().getOrDefault("name.hellstorm.mystery_crate"), price, false));
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
            if (phase != Phase.ROLLING) {
                spin.stop();
            } else if (box.contains(ELAPSED)) {
                spin.receive(until, duration, box.getInt(ELAPSED));
            }
        }

        /** What clients get: what's saved, and while rolling, how far into the roll the server is now, for {@link BoxSpin}. */
        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = super.getUpdateTag();
            if (phase == Phase.ROLLING && level != null) {
                tag.getCompound(STATE).putInt(ELAPSED, Mth.clamp((int) (level.getGameTime() - (until - duration)), 0, duration));
            }
            return tag;
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

        /** Both halves, the guns over them, and the lid where it falls open to one side. */
        @Override
        public AABB getRenderBoundingBox() {
            return new AABB(worldPosition).minmax(new AABB(worldPosition.relative(along(getBlockState()))))
                    .inflate(1, 0, 1).expandTowards(0, 1.5, 0);
        }
    }

    public MysteryBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BOX, Presence.STORED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BOX);
    }

    /** Places a whole crate, foot at {@code foot} and front facing {@code facing}, for code that doesn't go through a player placing the item. */
    public static void place(Level level, BlockPos foot, Direction facing) {
        place(level, foot, PapRegistries.MYSTERY_BOX.get().defaultBlockState().setValue(FACING, facing));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BOX).visible() ? SHAPE : Shapes.empty();
    }

    @Override
    protected BlockEntity newFootEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != PapRegistries.MYSTERY_BOX_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
                ? (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, entity) -> entity.clientTick()
                : (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, entity) -> entity.serverTick();
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.MYSTERY_BOX;
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
        return entity(level, pos).map(MysteryBoxBlock::describe).orElse(Component.literal("Mystery Crate"));
    }

    public static Component describe(Entity entity) {
        Config c = entity.config();
        return Component.literal("Mystery Crate location: pool " + c.pool() + ", cost " + c.cost().map(String::valueOf).orElse("default (box.cost)")
                + ". Set with /hs box pool <id> and /hs box cost <points|default>");
    }
}
