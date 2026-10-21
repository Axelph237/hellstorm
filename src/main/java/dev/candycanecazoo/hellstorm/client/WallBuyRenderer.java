package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the item a wall-buy sells flat on its plate, the way an item frame does. */
public final class WallBuyRenderer implements BlockEntityRenderer<WallBuyBlock.Entity> {
    /** Just in front of the plate, which is the block's back sixteenth, from the block's center toward the front ({@code FACING}). */
    private static final double DEPTH = 7.0 / 16 - 0.04;
    private static final float SCALE = 0.8f;

    private final ItemRenderer items;

    public WallBuyRenderer(BlockEntityRendererProvider.Context context) {
        items = context.getItemRenderer();
    }

    @Override
    public void render(WallBuyBlock.Entity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = entity.display();
        if (stack.isEmpty()) {
            return;
        }
        Direction facing = entity.getBlockState().getValue(WallBuyBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
        pose.translate(0, 0, DEPTH);
        pose.scale(SCALE, SCALE, SCALE);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), (int) entity.getBlockPos().asLong());
        pose.popPose();
    }
}
