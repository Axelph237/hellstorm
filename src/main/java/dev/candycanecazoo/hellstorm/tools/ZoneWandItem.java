package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.BlockAlignedItemRenderer;
import dev.candycanecazoo.hellstorm.client.BoneTextures;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Right-click sets selection corners, sneak-right-click clears. A corner is the air block in front of the clicked face,
 * so a zone holds the room's open space and two rooms sharing a wall don't overlap.
 */
public final class ZoneWandItem extends Item implements MapTool, GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ZoneWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(BlockAlignedItemRenderer.extensions(() -> new BlockAlignedItemRenderer<>(
                new DefaultedItemGeoModel<ZoneWandItem>(Hellstorm.id("zone_wand")), BoneTextures.NONE)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** The cell a click or hover selects, shared with the client renderer so the preview matches the server. */
    public static Optional<BlockPos> targetCell(Player player, double reach) {
        HitResult hit = player.pick(reach, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return Optional.of(blockHit.getBlockPos().relative(blockHit.getDirection()));
        }
        return Optional.empty();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (!serverPlayer.hasPermissions(2)) {
            dev.candycanecazoo.hellstorm.game.Notices.show(serverPlayer, Component.literal("Map tools need operator permission"));
            return InteractionResultHolder.fail(stack);
        }
        if (serverPlayer.isShiftKeyDown()) {
            ToolState.setSelection(serverPlayer, Selection.NONE);
            dev.candycanecazoo.hellstorm.game.Notices.show(serverPlayer, Component.literal("Selection cleared"));
            return InteractionResultHolder.success(stack);
        }
        Optional<BlockPos> cell = targetCell(serverPlayer, ServerConfig.TOOL_REACH.get());
        if (cell.isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        Selection next = ToolState.selection(serverPlayer).click(cell.get());
        ToolState.setSelection(serverPlayer, next);
        dev.candycanecazoo.hellstorm.game.Notices.show(serverPlayer, next.box()
                .map(box -> Component.literal("Selected " + box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ() + ". /hs zone add <zone>"))
                .orElse(Component.literal("First corner " + cell.get().toShortString())));
        return InteractionResultHolder.success(stack);
    }
}
