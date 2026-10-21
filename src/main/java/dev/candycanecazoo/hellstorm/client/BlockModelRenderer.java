package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.feature.TwoBlockMachine;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * A two-block machine's GeckoLib model, running from the foot toward the head along +z with its front on +x, turned so
 * the head is {@link TwoBlockMachine#along} and the front faces the block's facing. A model made the other way round,
 * front on -x, is turned about the seam. {@code offset}, in the model's pixels, moves a model made away from the origin onto its blocks.
 */
final class BlockModelRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T> {
    private final BoneTextures textures;
    private final Vec3 offset;
    private final boolean frontOnMinusX;

    BlockModelRenderer(GeoModel<T> model, BoneTextures textures, Vec3 offset) {
        this(model, textures, offset, false);
    }

    /** {@code frontOnMinusX}: the model was made with its front on its -x side, so it's drawn turned around. */
    BlockModelRenderer(GeoModel<T> model, BoneTextures textures, Vec3 offset, boolean frontOnMinusX) {
        super(model);
        this.textures = textures;
        this.offset = offset;
        this.frontOnMinusX = frontOnMinusX;
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(-TwoBlockMachine.along(facing).toYRot()));
        if (frontOnMinusX) {
            // Half a turn about the seam between the halves, half a block toward the head.
            pose.translate(0, 0, 1);
            pose.mulPose(Axis.YP.rotationDegrees(180));
        }
        pose.translate(offset.x / 16, offset.y / 16, offset.z / 16);
    }

    @Override
    public void renderRecursively(PoseStack pose, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        super.renderRecursively(pose, animatable, bone, renderType, buffers, textures.buffer(bone, buffers, renderType),
                isReRender, partialTick, light, overlay, red, green, blue, alpha);
    }
}
