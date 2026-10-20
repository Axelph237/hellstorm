package dev.candycanecazoo.packapunch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.candycanecazoo.packapunch.feature.PapMachineBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The item in a Pack-a-Punch machine, lying flat along the top of both halves across the seam: the item going in while
 * the fire spreads over it, then the upgraded item waiting to be taken.
 */
public final class PapMachineRenderer implements BlockEntityRenderer<PapMachineBlock.Entity> {
    /**
     * TacZ draws a gun about three blocks long in the item frame view, with its middle a quarter block below and a tenth
     * behind the point it's drawn at (measured on the AK-47). This brings it to under two blocks, centered across the table.
     */
    private static final float SCALE = 0.58f;
    private static final float CENTER_X = -0.017f;
    private static final float CENTER_Y = 0.243f;
    private final ItemRenderer items;

    public PapMachineRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(PapMachineBlock.Entity machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = machine.displayed();
        if (stack.isEmpty() || machine.getLevel() == null) {
            return;
        }
        Direction facing = machine.getBlockState().getValue(PapMachineBlock.FACING);
        pose.pushPose();
        pose.translate(0.5 + facing.getStepX() * 0.5, PapMachineBlock.TOP + 0.01, 0.5 + facing.getStepZ() * 0.5);
        // As a floor item frame does it: turn the item's front (which faces -z) around, lay it face up, then run its long
        // side (x) along the machine.
        pose.mulPose(Axis.YP.rotationDegrees(facing.getAxis() == Direction.Axis.Z ? 90 : 0));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(SCALE, SCALE, SCALE);
        // Laid face up, its back already rests on the table; center its length, and its height in the frame, which now runs across the table.
        pose.translate(CENTER_X, CENTER_Y, 0);
        items.renderStatic(stack, ItemDisplayContext.FIXED, LevelRenderer.getLightColor(machine.getLevel(), machine.getBlockPos().above()),
                OverlayTexture.NO_OVERLAY, pose, buffers, machine.getLevel(), (int) machine.getBlockPos().asLong());
        pose.popPose();
    }
}
