package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Drawn like {@code entity_translucent} but without writing depth, for glass with things behind it. The level draws items
 * after every block entity, so glass that wrote depth would hide the items behind it.
 */
final class SeeThrough extends RenderType {
    private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(texture -> create(Hellstorm.MOD_ID + "_see_through",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true)));

    private SeeThrough(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    static RenderType of(ResourceLocation texture) {
        return TYPES.apply(texture);
    }
}
