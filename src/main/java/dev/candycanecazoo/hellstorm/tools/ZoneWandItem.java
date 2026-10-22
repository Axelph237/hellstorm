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
 * Right-click sets selection corners, sneak-right-click clears. A corner is the block you're looking at. A finished
 * selection that touches no room hints at the add command.
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
            return Optional.of(blockHit.getBlockPos());
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
        hint(serverPlayer.serverLevel(), next).ifPresent(hint -> dev.candycanecazoo.hellstorm.game.Notices.show(serverPlayer, hint));
        return InteractionResultHolder.success(stack);
    }

    /** The add command, once both corners are set and the box touches no room. */
    public static Optional<Component> hint(ServerLevel level, Selection selection) {
        return selection.box()
                .filter(box -> MapSavedData.get(level).map().zones().values().stream().noneMatch(z -> z.boxes().stream().anyMatch(box::intersects)))
                .map(box -> Component.literal("/hs zones add <room|window|door>"));
    }
}
