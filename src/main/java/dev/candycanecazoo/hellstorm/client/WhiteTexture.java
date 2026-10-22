package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** One white pixel, made in memory, for shapes drawn in plain vertex colors with the entity shaders. */
public final class WhiteTexture {
    private static final ResourceLocation ID = Hellstorm.id("dynamic/white");
    private static boolean registered;

    private WhiteTexture() {}

    /** Call on the render thread. */
    public static ResourceLocation get() {
        if (!registered) {
            NativeImage pixel = new NativeImage(1, 1, false);
            pixel.setPixelRGBA(0, 0, 0xffffffff);
            Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(pixel));
            registered = true;
        }
        return ID;
    }
}
