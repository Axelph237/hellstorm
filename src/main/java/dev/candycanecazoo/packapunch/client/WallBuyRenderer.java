package dev.candycanecazoo.packapunch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.feature.WallBuyBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the item a wall-buy sells flat on its front, the way an item frame does: on a wall-buy's plate or a perk machine's panel. */
public final class WallBuyRenderer implements BlockEntityRenderer<WallBuyBlock.Entity> {
    /** Where the item sits from the block's center, toward the front ({@code FACING}), and how big it is. */
    private record Front(double depth, double lift, float scale) {}

    /** Just in front of the plate, which is the block's back sixteenth. */
    private static final Front PLATE = new Front(7.0 / 16 - 0.04, 0, 0.8f);
    /** Just in front of the glowstone panel, which stands half a pixel out of the cabinet's front, centered on it. */
    private static final Front MACHINE = new Front(-(0.5 - 0.5 / 16) - 0.02,-1.0 / 16, 0.5f);

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
        Front front = entity.getBlockState().getBlock() instanceof PerkMachineBlock ? MACHINE : PLATE;
        Direction facing = entity.getBlockState().getValue(WallBuyBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5 + front.lift(), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
        pose.translate(0, 0, front.depth());
        pose.scale(front.scale(), front.scale(), front.scale());
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), (int) entity.getBlockPos().asLong());
        pose.popPose();
    }
}
