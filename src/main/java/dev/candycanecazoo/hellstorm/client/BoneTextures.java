package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.candycanecazoo.hellstorm.feature.PapMachineBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;

/**
 * GeckoLib draws a model with a single texture. A model that borrows vanilla textures for some parts names them here,
 * by top-level bone; every other bone keeps the model's own texture. The UVs carry over because GeckoLib scales them by
 * the model's texture size, not the image's.
 */
public record BoneTextures(Map<String, ResourceLocation> byBone) {
    public static final BoneTextures NONE = new BoneTextures(Map.of());
    public static final BoneTextures HELLFIRE_UPGRADER = new BoneTextures(Map.of(
            "candles", new ResourceLocation("minecraft", "textures/block/candle_lit.png"),
            "skull", new ResourceLocation("minecraft", "textures/entity/skeleton/skeleton.png")));

    /** The textures for the GeckoLib model {@code model}. */
    public static BoneTextures of(String model) {
        return model.equals(PapMachineBlock.MODEL) ? HELLFIRE_UPGRADER : NONE;
    }

    /** The buffer for {@code bone}. Fetched fresh for every bone, since fetching another texture's ends the previous batch. */
    VertexConsumer buffer(GeoBone bone, MultiBufferSource buffers, RenderType modelType) {
        GeoBone root = bone;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        ResourceLocation texture = byBone.get(root.getName());
        return buffers.getBuffer(texture == null ? modelType : RenderType.entityCutoutNoCull(texture));
    }
}
