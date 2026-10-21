package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
 * A machine's GeckoLib model, turned so the model's {@code front} side faces the way the block's front does, then moved
 * {@code offset} model pixels onto its blocks. The model's origin is the middle of the floor of the machine's main block.
 */
final class BlockModelRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T> {
    private final BoneTextures textures;
    private final Direction front;
    private final Vec3 offset;

    /** {@code front}: the side of the model that is its front, as a direction in the model's own space. */
    BlockModelRenderer(GeoModel<T> model, BoneTextures textures, Direction front, Vec3 offset) {
        super(model);
        this.textures = textures;
        this.front = front;
        this.offset = offset;
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack pose) {
        turn(pose, facing, front, offset);
    }

    /** Turns a model whose front is its {@code front} side so the front faces {@code facing}, then moves it {@code offset} pixels. */
    static void turn(PoseStack pose, Direction facing, Direction front, Vec3 offset) {
        pose.mulPose(Axis.YP.rotationDegrees(front.toYRot() - facing.toYRot()));
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
