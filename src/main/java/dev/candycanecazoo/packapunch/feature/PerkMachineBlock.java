package dev.candycanecazoo.packapunch.feature;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * A wall-buy in a machine cabinet: it sells any item the same way and shows it on its front. By default it sells a
 * Juggernog potion and needs the power. The cabinet is tinted with the color of the potion it sells.
 */
public final class PerkMachineBlock extends HorizontalDirectionalBlock implements EntityBlock, FeatureBlock {
    public static final int DEFAULT_COST = 2500;
    public static final WallBuy.Config DEFAULT = sells(PackAPunch.id("juggernog"), DEFAULT_COST);

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
                    Legacy.CODEC.flatXmap(Legacy::config, c -> DataResult.error(() -> "Perk machines save as wall-buy settings")))
            .xmap(either -> either.map(c -> c, c -> c), Either::left);

    public PerkMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Sells one potion of {@code potion}, once per player: buying again is refused. */
    public static WallBuy.Config sells(ResourceLocation potion, int cost) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(PotionUtils.TAG_POTION, potion.toString());
        ItemSpec item = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "potion"), 1, Optional.of(nbt));
        return new WallBuy.Config(item, cost, WallBuy.Repurchase.DENY, cost / 2, WallBuy.DEFAULT_PAP_AMMO_COST, true);
    }

    public static WallBuyBlock.Entity newEntity(BlockPos pos, BlockState state) {
        return new WallBuyBlock.Entity(PapRegistries.PERK_MACHINE_ENTITY.get(), pos, state, CODEC, DEFAULT);
    }

    /** The potion's id for a potion, so a script sees which perk was bought; the item spec otherwise. */
    static String describe(ItemStack sold, ItemSpec spec) {
        Potion potion = PotionUtils.getPotion(sold);
        return potion == Potions.EMPTY ? spec.describe() : String.valueOf(ForgeRegistries.POTIONS.getKey(potion));
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
        return newEntity(pos, state);
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
        WallBuyBlock.entity(game.level(), pos).ifPresent(entity -> WallBuyBlock.sell(player, game, entity,
                (buyer, cost, refill) -> new GameEvent.PerkBought(buyer, describe(entity.display(), entity.config().item()), cost)));
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos) {
        return WallBuyBlock.entity(level, pos).map(WallBuyBlock::describe).orElse(Component.literal("Perk machine"));
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
