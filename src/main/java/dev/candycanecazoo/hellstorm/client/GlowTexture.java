package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * A soft round glow, made in memory: white in the middle, fading to black at the edge. It's drawn added onto what's
 * behind it ({@code RenderType.eyes}), so black adds nothing and the vertex color sets its color and strength.
 */
public final class GlowTexture {
    private static final ResourceLocation ID = Hellstorm.id("dynamic/glow");
    private static final int SIZE = 32;
    private static boolean registered;

    private GlowTexture() {}

    /** Call on the render thread. */
    public static ResourceLocation get() {
        if (!registered) {
            NativeImage image = new NativeImage(SIZE, SIZE, false);
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    double dx = (x + 0.5) / SIZE * 2 - 1;
                    double dy = (y + 0.5) / SIZE * 2 - 1;
                    double fall = Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy));
                    int v = (int) Math.round(255 * fall * fall);
                    image.setPixelRGBA(x, y, 0xff000000 | v << 16 | v << 8 | v);
                }
            }
            Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(image));
            registered = true;
        }
        return ID;
    }
}
