package dev.candycanecazoo.hellstorm.feature;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.VendingMachineHum;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.Refusal;
import dev.candycanecazoo.hellstorm.game.Shop;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * A wall-buy in a vending machine two blocks wide and two tall ({@link TwoBlockMachine}). It sells any item the same way
 * and stocks its shelves with it. Buying presses three keys and shakes the machine for {@link #VEND_TICKS}, and then the
 * item is the buyer's. By default it sells a Vigoraid potion and needs the power. While it can sell, it is lit and hums.
 */
public final class VendingMachineBlock extends TwoBlockMachine {
    public static final int DEFAULT_COST = 2500;
    public static final WallBuy.Config DEFAULT = sells(Hellstorm.id("vigorade"), DEFAULT_COST);
    /** The GeckoLib model, animation and unlit texture, under {@code geo/block/}, {@code animations/block/} and {@code textures/block/}. */
    public static final String MODEL = "vending_machine";
    /** The model is 24 pixels wide, with its middle 4 pixels toward the foot from the seam between its blocks. */
    public static final Vec3 MODEL_OFFSET = new Vec3(4, 0, 0);
    /**
     * The model's use animation, and how long a vend takes: until its last movement, the end of the shake, 1.58 seconds in.
     * The item is handed over then. The animation itself runs to 4 seconds, still after that.
     */
    public static final String USE = "use";
    public static final int VEND_TICKS = 32;
    /** When each of the use animation's three keys hits bottom, and its click's pitch. */
    private static final int[] PRESS_TICKS = {3, 6, 9};
    private static final float[] PRESS_PITCHES = {1.0f, 1.12f, 0.94f};
    /** How long the machine takes to light up or go dark. */
    public static final int FADE_TICKS = 20;
    /** Each block's shape, by the side of it away from the other half: the model is 4 pixels short of the blocks' ends. */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.WEST, Block.box(4, 0, 0, 16, 16, 16),
            Direction.EAST, Block.box(0, 0, 0, 12, 16, 16),
            Direction.NORTH, Block.box(0, 0, 4, 16, 16, 16),
            Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 12));

    /** Machines saved before perks were potions: {@code {perk, cost}}, read as a potion with the perk's id. */
    private record Legacy(ResourceLocation perk, Optional<Integer> cost) {
        static final Codec<Legacy> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("perk").forGetter(Legacy::perk),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(Legacy::cost)
        ).apply(i, Legacy::new));

        DataResult<WallBuy.Config> config() {
            return ForgeRegistries.POTIONS.containsKey(perk)
                    ? DataResult.success(sells(perk, cost.orElse(DEFAULT_COST)))
                    : DataResult.error(() -> "No potion " + perk + " for the old perk machine setting");
        }
    }

    /** A wall-buy's settings; the old perk shape still loads, and is saved as a wall-buy's from then on. */
    public static final Codec<WallBuy.Config> CODEC = Codec.either(WallBuy.Config.CODEC,
                    Legacy.CODEC.flatXmap(Legacy::config, c -> DataResult.error(() -> "Vending machines save as wall-buy settings")))
            .xmap(either -> either.map(c -> c, c -> c), Either::left);

    /** A paid-for item on its way out: handed to {@code buyer} through {@code sale} at {@code until}. */
    private record Vend(ServerPlayer buyer, long until, Shop.Sale sale) {}

    public static final class Entity extends WallBuyBlock.Entity implements GeoBlockEntity {
        private static final RawAnimation USE_ANIMATION = RawAnimation.begin().thenPlay(USE);
        private static final String ON = "on";
        private static final String VEND_UNTIL = "vend_until";
        private static final long NEVER = Long.MIN_VALUE / 2;

        private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
        /** The server's vend. Not saved: a machine unloaded mid-vend keeps the points. */
        private Optional<Vend> vend = Optional.empty();
        /** Synced: whether it can sell, and when the vend ends in server time (0 for none). */
        private boolean on;
        private long vendUntil;
        /** Client only, in client time: when it last lit up or went dark, when the vend it's drawing began, and its last tick of it. */
        private long switchedAt = NEVER;
        private long vendStarted = NEVER;
        private long vendHeard = -1;

        public Entity(BlockPos pos, BlockState state) {
            super(PapRegistries.VENDING_MACHINE_ENTITY.get(), pos, state, CODEC, DEFAULT);
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, USE, 0, state -> {
                if (level != null && vendTick(level.getGameTime()) >= 0) {
                    return state.setAndContinue(USE_ANIMATION);
                }
                // So the next vend plays from the start, even though the controller last played the same animation.
                state.getController().forceAnimationReset();
                return PlayState.STOP;
            }));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return animations;
        }

        void use(ServerPlayer player, GameManager.Active game) {
            Shop.Sale sale = WallBuyBlock.sale(player, game, this,
                    (buyer, cost, refill) -> new GameEvent.PerkBought(buyer, describe(display(), config().item()), cost));
            Shop.buy(player, game, new Shop.Sale() {
                @Override
                public int cost() {
                    return sale.cost();
                }

                @Override
                public boolean needsPower() {
                    return sale.needsPower();
                }

                @Override
                public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                    return vend.isPresent() ? Optional.of(new Refusal.Because("The machine is busy")) : sale.check(g, p);
                }

                @Override
                public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                    vend = Optional.of(new Vend(p, g.level().getGameTime() + VEND_TICKS, sale));
                    vendUntil = vend.get().until();
                    changed();
                    return Optional.empty();
                }
            });
        }

        void serverTick(ServerLevel level) {
            boolean canSell = game(level).map(a -> !config().requiresPower() || a.game().powerOn()).orElse(false);
            if (canSell != on) {
                on = canSell;
                changed();
            }
            if (vend.isPresent() && level.getGameTime() >= vend.get().until()) {
                handOver(level);
            }
        }

        /** The game this machine is part of. */
        private Optional<GameManager.Active> game(ServerLevel level) {
            return GameManager.get(level).filter(a -> a.game().map().map().features().get(worldPosition) == FeatureKind.VENDING_MACHINE);
        }

        /**
         * Hands over the item being vended, as the end of the use animation does, refunding it if the buyer can't take it
         * now. Tests that only need a bought item call it to skip the wait.
         */
        public void handOver(ServerLevel level) {
            if (vend.isEmpty()) {
                return;
            }
            Vend done = vend.get();
            clear();
            Optional<GameManager.Active> game = game(level);
            ServerPlayer buyer = done.buyer();
            if (game.isEmpty() || buyer.hasDisconnected()) {
                return;
            }
            Optional<Refusal> refused = done.sale().deliver(game.get(), buyer);
            if (refused.isPresent()) {
                game.get().game().refund(buyer.getUUID(), done.sale().cost());
                Shop.refuse(buyer, refused.get());
            }
        }

        /** A vend in progress is called off, with nothing handed over. */
        void clear() {
            if (vend.isPresent() || vendUntil != 0) {
                vend = Optional.empty();
                vendUntil = 0;
                changed();
            }
        }

        /** The clicks of the keys, in time with the use animation, and the hum while lit. */
        void clientTick() {
            long tick = vendTick(level.getGameTime());
            if (tick > vendHeard) {
                Vec3 keys = center();
                for (int i = 0; i < PRESS_TICKS.length; i++) {
                    if (vendHeard < PRESS_TICKS[i] && tick >= PRESS_TICKS[i]) {
                        level.playLocalSound(keys.x, keys.y, keys.z, PapRegistries.VENDING_MACHINE_BUTTON.get(), SoundSource.BLOCKS, 0.8f, PRESS_PITCHES[i], false);
                    }
                }
            }
            vendHeard = tick;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VendingMachineHum.keep(this));
        }

        /** Client side: ticks into the vend being drawn at {@code now}, or -1 when there's none. */
        public long vendTick(long now) {
            long tick = now - vendStarted;
            return tick >= 0 && tick < VEND_TICKS ? tick : -1;
        }

        /** Client side: how lit it is {@code partialTick} after {@code now}, from 0 (dark) to 1, fading over {@link #FADE_TICKS} when it switches. */
        public float lit(long now, float partialTick) {
            float switched = Mth.clamp((now - switchedAt + partialTick) / FADE_TICKS, 0, 1);
            return on ? switched : 1 - switched;
        }

        /** The middle of the machine, on the seam between its blocks. */
        public Vec3 center() {
            return Vec3.atLowerCornerOf(worldPosition).add(0.5, 1, 0.5).add(Vec3.atLowerCornerOf(along(getBlockState()).getNormal()).scale(0.5));
        }

        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = super.getUpdateTag();
            tag.putBoolean(ON, on);
            tag.putLong(VEND_UNTIL, vendUntil);
            return tag;
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            boolean wasOn = on;
            long wasUntil = vendUntil;
            on = tag.getBoolean(ON);
            vendUntil = tag.getLong(VEND_UNTIL);
            if (level == null || !level.isClientSide) {
                return;
            }
            if (on != wasOn) {
                switchedAt = level.getGameTime();
            }
            if (vendUntil != wasUntil) {
                // The client's clock, so the sounds keep time with the animation, which starts when this arrives.
                vendStarted = vendUntil != 0 ? level.getGameTime() : NEVER;
                vendHeard = -1;
            }
        }

        @Override
        public AABB getRenderBoundingBox() {
            return new AABB(worldPosition).minmax(new AABB(worldPosition.relative(along(getBlockState())))).expandTowards(0, 1, 0);
        }
    }

    public VendingMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    /** Places a whole machine, lower foot at {@code foot} and front facing {@code facing}, for code that doesn't go through a player placing the item. */
    public static void place(Level level, BlockPos foot, Direction facing) {
        place(level, foot, PapRegistries.VENDING_MACHINE.get().defaultBlockState().setValue(FACING, facing));
    }

    /** Sells one potion of {@code potion}, once per player: buying again is refused. */
    public static WallBuy.Config sells(ResourceLocation potion, int cost) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(PotionUtils.TAG_POTION, potion.toString());
        ItemSpec item = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "potion"), 1, Optional.of(nbt));
        return new WallBuy.Config(item, cost, WallBuy.Repurchase.DENY, cost / 2, WallBuy.DEFAULT_PAP_AMMO_COST, true);
    }

    /** The potion's id for a potion, so a script sees which perk was bought; the item spec otherwise. */
    static String describe(ItemStack sold, ItemSpec spec) {
        Potion potion = PotionUtils.getPotion(sold);
        return potion == Potions.EMPTY ? spec.describe() : String.valueOf(ForgeRegistries.POTIONS.getKey(potion));
    }

    /** Calls off every machine's vend. */
    public static void resetAll(ServerLevel level, Collection<BlockPos> machines) {
        for (BlockPos pos : machines) {
            if (level.getBlockEntity(pos) instanceof Entity machine) {
                machine.clear();
            }
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction along = along(state);
        return SHAPES.get(state.getValue(PART) == BedPart.FOOT ? along.getOpposite() : along);
    }

    @Override
    protected BlockEntity newFootEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != PapRegistries.VENDING_MACHINE_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
                ? (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, machine) -> machine.clientTick()
                : (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, machine) -> machine.serverTick((ServerLevel) l);
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.VENDING_MACHINE;
    }

    @Override
    public void buy(ServerPlayer player, GameManager.Active game, BlockPos pos, BlockState state) {
        if (game.level().getBlockEntity(pos) instanceof Entity machine) {
            machine.use(player, game);
        }
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos) {
        return WallBuyBlock.entity(level, pos).map(WallBuyBlock::describe).orElse(getName());
    }
}
