package dev.candycanecazoo.hellstorm.feature;

import com.mojang.serialization.Codec;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.data.PurchaseDefaults;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.Refusal;
import dev.candycanecazoo.hellstorm.game.Shop;
import dev.candycanecazoo.hellstorm.game.WeaponSlots;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.perk.Perks;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A plate on a wall that sells one item, drawn on its face. {@code FACING} is the way the face points, away from the wall.
 * Buying again follows the wall-buy's {@link WallBuy.Repurchase} rule.
 */
public final class WallBuyBlock extends HorizontalDirectionalBlock implements EntityBlock, FeatureBlock {
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(0, 0, 15, 16, 16, 16),
            Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 1),
            Direction.EAST, Block.box(0, 0, 0, 1, 16, 16),
            Direction.WEST, Block.box(15, 0, 0, 16, 16, 16));
    private static final String TIER = "tier";

    public static class Entity extends ConfigBlockEntity<WallBuy.Config> implements Prompted {
        private static final String DISPLAY = "display";

        private ItemStack display;
        private ItemStack synced = ItemStack.EMPTY;

        public Entity(BlockPos pos, BlockState state) {
            this(PapRegistries.WALL_BUY_ENTITY.get(), pos, state, WallBuy.Config.CODEC, WallBuy.Config.DEFAULT);
        }

        /** A wall-buy on another block, with that block's defaults, such as the vending machine. */
        public Entity(BlockEntityType<?> type, BlockPos pos, BlockState state, Codec<WallBuy.Config> codec, WallBuy.Config defaults) {
            super(type, pos, state, codec, defaults);
        }

        @Override
        public void setConfig(WallBuy.Config config) {
            display = null;
            super.setConfig(config);
        }

        @Override
        protected Optional<String> problem(WallBuy.Config config) {
            return WallBuy.problem(config);
        }

        /** The item as players see it. Built on the server and synced, since a client can't always build TacZ guns. */
        public ItemStack display() {
            if (level != null && level.isClientSide) {
                return synced;
            }
            if (display == null) {
                display = config().item().build(0).orElse(ItemStack.EMPTY);
            }
            return display;
        }

        public String itemName() {
            ItemStack stack = display();
            return stack.isEmpty() ? config().item().describe() : stack.getHoverName().getString();
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            display = null;
            synced = tag.contains(DISPLAY) ? ItemStack.of(tag.getCompound(DISPLAY)) : ItemStack.EMPTY;
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            ItemStack stack = display();
            if (!stack.isEmpty()) {
                tag.put(DISPLAY, stack.save(new CompoundTag()));
            }
        }

        @Override
        public Optional<Prompt> prompt() {
            WallBuy.Action action = Viewer.get().map(p -> action(config(), display(), p, find(p.getInventory(), config().item())))
                    .orElseGet(() -> WallBuy.decide(config(), Optional.empty()));
            return action instanceof WallBuy.Action.Deny ? Optional.empty() : Optional.of(new Prompt(itemName(), action.cost(), config().requiresPower()));
        }
    }

    /** What a purchase tells scripts: {@code wall_buy} from a wall-buy, {@code perk_bought} from a vending machine. */
    @FunctionalInterface
    public interface Receipt {
        GameEvent of(UUID player, int cost, boolean refill);
    }

    /**
     * What using the wall-buy would do for {@code player}: refused for a potion whose effects they all have already,
     * otherwise the repurchase rule for what they carry ({@code match}).
     */
    public static WallBuy.Action action(WallBuy.Config config, ItemStack sold, Player player, Optional<Match> match) {
        return Perks.alreadyHas(player, sold).<WallBuy.Action>map(WallBuy.Action.Deny::new)
                .orElseGet(() -> WallBuy.decide(config, match.map(Match::owned)));
    }

    /** The stack a repurchase acts on (the held one first), and what the player has of the item in total. */
    public record Match(ItemStack stack, WallBuy.Owned owned) {}

    public WallBuyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public static Optional<Match> find(Inventory inventory, ItemSpec item) {
        List<ItemStack> matches = new ArrayList<>();
        if (item.matches(inventory.getSelected())) {
            matches.add(inventory.getSelected());
        }
        for (List<ItemStack> section : List.of(inventory.items, inventory.offhand)) {
            for (ItemStack stack : section) {
                if (stack != inventory.getSelected() && item.matches(stack)) {
                    matches.add(stack);
                }
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        int count = matches.stream().mapToInt(ItemStack::getCount).sum();
        ItemStack first = matches.get(0);
        return Optional.of(new Match(first, new WallBuy.Owned(count, punched(first), ammo(first))));
    }

    private static WallBuy.Ammo ammo(ItemStack stack) {
        if (TaczBridge.hasUnlimitedAmmo(stack)) {
            return WallBuy.Ammo.UNLIMITED;
        }
        return TaczBridge.ammoFull(stack) ? WallBuy.Ammo.FULL : WallBuy.Ammo.ROOM;
    }

    /** The upgrader writes its tier into the item's {@code pap} compound. */
    public static boolean punched(ItemStack stack) {
        CompoundTag pap = stack.getTagElement(ItemSpec.PAP);
        return pap != null && pap.getInt(TIER) > 0;
    }

    /** Whether all of {@code stack} fits in the inventory, merging into partial stacks first. */
    public static boolean fits(Inventory inventory, ItemStack stack) {
        int room = 0;
        for (ItemStack slot : inventory.items) {
            if (slot.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(slot, stack)) {
                room += Math.max(0, Math.min(slot.getMaxStackSize(), inventory.getMaxStackSize()) - slot.getCount());
            }
            if (room >= stack.getCount()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands over a bought item: a gun through the weapon-slot rule, anything else into the inventory. Refuses without
     * giving anything if it can't all fit.
     */
    public static Optional<Refusal> give(ServerPlayer player, GameSettings settings, ItemStack stack) {
        if (WeaponSlots.isGun(stack)) {
            return WeaponSlots.give(player, settings, stack);
        }
        if (!fits(player.getInventory(), stack)) {
            return Optional.of(new Refusal.Because(WeaponSlots.FULL));
        }
        player.getInventory().add(stack);
        player.inventoryMenu.broadcastChanges();
        return Optional.empty();
    }

    /** {@link #give}'s refusal, checked before charging. */
    public static Optional<Refusal> canGive(ServerPlayer player, GameSettings settings, ItemStack stack) {
        if (WeaponSlots.isGun(stack)) {
            return WeaponSlots.check(player, settings);
        }
        return fits(player.getInventory(), stack) ? Optional.empty() : Optional.of(new Refusal.Because(WeaponSlots.FULL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.WALL_BUY;
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
        entity(game.level(), pos).ifPresent(entity -> sell(player, game, entity,
                (buyer, cost, refill) -> new GameEvent.WallBuy(buyer, entity.config().item().describe(), cost, refill)));
    }

    /** Sells what {@code entity} holds through {@link Shop}, for a wall-buy and any block that sells like one. */
    public static void sell(ServerPlayer player, GameManager.Active game, Entity entity, Receipt receipt) {
        Shop.buy(player, game, sale(player, game, entity, receipt));
    }

    /** What selling {@code entity}'s item to {@code player} costs, checks, and hands over, for {@link Shop#buy}. */
    public static Shop.Sale sale(ServerPlayer player, GameManager.Active game, Entity entity, Receipt receipt) {
        WallBuy.Config config = entity.config();
        GameSettings settings = game.game().settings();
        boolean playing = game.game().player(player.getUUID()).isPresent();
        Optional<Match> match = find(player.getInventory(), config.item());
        WallBuy.Action action = action(config, entity.display(), player, match);
        Optional<ItemStack> stock = config.item().build(settings.players().reserveMagazines());
        if (action instanceof WallBuy.Action.TopUp topUp) {
            stock.ifPresent(s -> s.setCount(topUp.missing()));
        }
        return new Shop.Sale() {
            @Override
            public int cost() {
                return action.cost();
            }

            @Override
            public boolean needsPower() {
                return config.requiresPower();
            }

            @Override
            public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                if (action instanceof WallBuy.Action.Deny deny) {
                    return Optional.of(new Refusal.Because(deny.reason()));
                }
                if (!playing) {
                    return Optional.of(new Refusal.NotPlaying());
                }
                if (action instanceof WallBuy.Action.Refill) {
                    return Optional.empty();
                }
                if (stock.isEmpty()) {
                    return Optional.of(new Refusal.Because(config.item().describe() + " isn't installed"));
                }
                return canGive(p, settings, stock.get());
            }

            @Override
            public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                boolean refill = action instanceof WallBuy.Action.Refill;
                if (refill) {
                    if (!TaczBridge.refillAmmo(match.get().stack())) {
                        return Optional.of(new Refusal.Because("That gun can't be refilled"));
                    }
                    p.inventoryMenu.broadcastChanges();
                } else {
                    Optional<Refusal> refused = give(p, settings, stock.get());
                    if (refused.isPresent()) {
                        return refused;
                    }
                }
                g.game().events().emit(receipt.of(p.getUUID(), action.cost(), refill));
                return Optional.empty();
            }
        };
    }

    public static Optional<Entity> entity(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Entity entity ? Optional.of(entity) : Optional.empty();
    }

    /** A wall-buy placed by hand starts with its gun's listed defaults; one copied with its settings keeps them. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && BlockItem.getBlockEntityData(stack) == null && level.getBlockEntity(pos) instanceof Entity entity) {
            WallBuy.Config config = entity.config();
            PurchaseDefaults.of(config.item()).ifPresent(defaults -> entity.setConfig(config.selling(config.item(), defaults)));
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
    }
}
