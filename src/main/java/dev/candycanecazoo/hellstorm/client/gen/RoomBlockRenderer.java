package dev.candycanecazoo.hellstorm.client.gen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.candycanecazoo.hellstorm.gen.block.RoomBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/** Outlines the box a Room Block saves, like a structure block's, so a map maker sees what's in the room. */
public final class RoomBlockRenderer implements BlockEntityRenderer<RoomBlock.Entity> {
    public RoomBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(RoomBlock.Entity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockPos offset = entity.config().offset();
        Vec3i size = entity.config().size();
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), offset.getX(), offset.getY(), offset.getZ(),
                offset.getX() + size.getX(), offset.getY() + size.getY(), offset.getZ() + size.getZ(), 0.9f, 0.9f, 0.9f, 1f, 0.5f, 0.5f, 0.5f);
    }

    @Override
    public boolean shouldRenderOffScreen(RoomBlock.Entity entity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
