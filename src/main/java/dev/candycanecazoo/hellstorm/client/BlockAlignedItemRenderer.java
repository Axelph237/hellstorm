package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.function.Supplier;

/**
 * A GeckoLib item drawn where a block model would be, its floor at the bottom of the item's cube, so the item model's
 * display transforms mean what they do for a block. GeckoLib lifts item models 0.51 off that floor.
 */
public class BlockAlignedItemRenderer<T extends Item & GeoAnimatable> extends GeoItemRenderer<T> {
    private final BoneTextures textures;

    public BlockAlignedItemRenderer(GeoModel<T> model, BoneTextures textures) {
        super(model);
        this.textures = textures;
    }

    /** Draws an item with the renderer {@code factory} makes, the first time the item is drawn. */
    public static IClientItemExtensions extensions(Supplier<BlockAlignedItemRenderer<?>> factory) {
        return new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = factory.get();
                }
                return renderer;
            }
        };
    }

    @Override
    public void preRender(PoseStack pose, T animatable, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
        super.preRender(pose, animatable, model, buffers, buffer, isReRender, partialTick, light, overlay, red, green, blue, alpha);
        if (!isReRender) {
            pose.translate(0, -0.51f, 0);
        }
    }

    @Override
    public void renderRecursively(PoseStack pose, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        super.renderRecursively(pose, animatable, bone, renderType, buffers, textures.buffer(bone, buffers, renderType),
                isReRender, partialTick, light, overlay, red, green, blue, alpha);
    }
}
