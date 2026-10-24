package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.BlockAlignedItemRenderer;
import dev.candycanecazoo.hellstorm.client.BoneTextures;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Right-click sets selection corners, and every two add a box to the selection (see {@link Selection}). A corner is the
 * cell above the block you're looking at, or, sneaking, that block itself. A click never uses the block (no door opens, no
 * lever flips). {@code /hs zones desel} clears the selection. Finishing a box when the selection touches no room hints at
 * the add command.
 */
public final class ZoneWandItem extends Item implements MapTool, GeoItem {
    /** The two frames turning, from {@code animations/item/zone_wand.animation.json}. */
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("zone_wand_spin");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ZoneWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(BlockAlignedItemRenderer.extensions(() -> new BlockAlignedItemRenderer<>(
                new DefaultedItemGeoModel<ZoneWandItem>(Hellstorm.id("zone_wand")), BoneTextures.NONE, Vec3.ZERO)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** The cell a click or hover selects, shared with the client renderer so the preview matches the server. */
    public static Optional<BlockPos> targetCell(Player player, double reach) {
        HitResult hit = player.pick(reach, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return Optional.of(cellFor(player, blockHit.getBlockPos()));
        }
        return Optional.empty();
    }

    /** Above the block looked at, so a room's corner lands in the air over its floor; sneaking, the block itself. */
    public static BlockPos cellFor(Player player, BlockPos lookedAt) {
        return player.isShiftKeyDown() ? lookedAt : lookedAt.above();
    }

    /** A click on a block within reach: selects, and never uses the block, sneaking or not. Runs before the block would. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            select(player, cellFor(player, context.getClickedPos()));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    /** A click past the usual reach, up to the tools' own ({@code tools.reach}). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        Optional<BlockPos> cell = targetCell(serverPlayer, ServerConfig.TOOL_REACH.get());
        if (cell.isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        select(serverPlayer, cell.get());
        return InteractionResultHolder.success(stack);
    }

    private static void select(ServerPlayer player, BlockPos cell) {
        if (!player.hasPermissions(2)) {
            dev.candycanecazoo.hellstorm.game.Notices.show(player, Component.literal("Map tools need operator permission"));
            return;
        }
        Selection next = ToolState.selection(player).click(cell);
        ToolState.setSelection(player, next);
        hint(player.serverLevel(), next).ifPresent(hint -> dev.candycanecazoo.hellstorm.game.Notices.show(player, hint));
    }

    /** The add command, once a box is finished (no corner waiting for its pair) and no box of the selection touches a room. */
    public static Optional<Component> hint(ServerLevel level, Selection selection) {
        if (selection.isEmpty() || selection.corner().isPresent()) {
            return Optional.empty();
        }
        boolean touchesRoom = MapSavedData.get(level).map().zones().values().stream()
                .anyMatch(z -> z.boxes().stream().anyMatch(b -> selection.boxes().stream().anyMatch(b::intersects)));
        return touchesRoom ? Optional.empty() : Optional.of(Component.literal("/hs zones add <room|window|door>"));
    }
}
