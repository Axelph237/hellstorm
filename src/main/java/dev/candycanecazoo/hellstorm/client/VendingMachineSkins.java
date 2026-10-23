package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.VendingMachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The vending machine's textures in the color of what it sells. An item with a color of its own (a potion's liquid, dyed
 * leather, anything with an item color on its first layer) gets the machine repainted around that color by
 * {@link MachineShades}, the unlit texture and the lit strip both, built in memory the first time the color is seen.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VendingMachineSkins {
    public record Skin(ResourceLocation unlit, ResourceLocation lit) {}

    public static final Skin GREEN = new Skin(Hellstorm.id("textures/block/" + VendingMachineBlock.MODEL + ".png"),
            Hellstorm.id("textures/block/" + VendingMachineBlock.MODEL + "_on.png"));
    /** Each color holds the lit strip, 128 by 5760, so only the most recently drawn are kept. */
    private static final int MAX_COLORS = 32;

    private static final Map<Integer, Skin> SKINS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Skin> eldest) {
            if (size() > MAX_COLORS) {
                release(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    private VendingMachineSkins() {}

    public static Skin of(ItemStack stack) {
        if (stack.isEmpty()) {
            return GREEN;
        }
        int color = Minecraft.getInstance().getItemColors().getColor(stack, 0);
        return color == -1 ? GREEN : SKINS.computeIfAbsent(color & 0xffffff, VendingMachineSkins::paint);
    }

    private static Skin paint(int color) {
        try {
            NativeImage unlit = read(GREEN.unlit());
            Optional<MachineShades.Basis> basis = MachineShades.basis(pixels(unlit));
            if (basis.isEmpty()) {
                unlit.close();
                return GREEN;
            }
            NativeImage lit = read(GREEN.lit());
            MachineShades.Hsv target = MachineShades.Hsv.of(color);
            repaint(unlit, basis.get(), target);
            repaint(lit, basis.get(), target);
            String hex = String.format("%06x", color);
            Skin skin = new Skin(Hellstorm.id("dynamic/" + VendingMachineBlock.MODEL + "_" + hex), Hellstorm.id("dynamic/" + VendingMachineBlock.MODEL + "_on_" + hex));
            Minecraft.getInstance().getTextureManager().register(skin.unlit(), new DynamicTexture(unlit));
            Minecraft.getInstance().getTextureManager().register(skin.lit(), new DynamicTexture(lit));
            return skin;
        } catch (IOException e) {
            Hellstorm.LOG.warn("Couldn't repaint the vending machine for color {}", String.format("#%06x", color), e);
            return GREEN;
        }
    }

    private static NativeImage read(ResourceLocation texture) throws IOException {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(texture)) {
            return NativeImage.read(in);
        }
    }

    /** {@link NativeImage} pixels are packed {@code 0xAABBGGRR}; these are {@code 0xRRGGBB}, transparent ones left out. */
    private static int[] pixels(NativeImage image) {
        return IntStream.range(0, image.getWidth() * image.getHeight())
                .map(i -> image.getPixelRGBA(i % image.getWidth(), i / image.getWidth()))
                .filter(abgr -> abgr >>> 24 != 0)
                .map(VendingMachineSkins::swap)
                .toArray();
    }

    /** Swaps the red and blue bytes, which turns {@code 0xAABBGGRR} into {@code 0xAARRGGBB} and back. */
    private static int swap(int color) {
        return color & 0xff00ff00 | (color & 0xff) << 16 | color >> 16 & 0xff;
    }

    /** The strip has only a few colors, so each is worked out once. Its frames are square, one under another. */
    private static void repaint(NativeImage image, MachineShades.Basis basis, MachineShades.Hsv target) {
        Map<Integer, Integer> painted = new HashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                if (abgr >>> 24 == 0) {
                    continue;
                }
                int rgb = swap(abgr) & 0xffffff;
                if (MachineShades.keypadLight(x, y % image.getWidth(), rgb)) {
                    continue;
                }
                int next = painted.computeIfAbsent(rgb, c -> MachineShades.recolor(c, basis, target));
                if (next != rgb) {
                    image.setPixelRGBA(x, y, swap(abgr & 0xff000000 | next));
                }
            }
        }
    }

    private static void release(Skin skin) {
        if (skin != GREEN) {
            Minecraft.getInstance().getTextureManager().release(skin.unlit());
            Minecraft.getInstance().getTextureManager().release(skin.lit());
        }
    }

    /** A resource pack can change the machine's own textures, so they're repainted again after a reload. */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            SKINS.values().forEach(VendingMachineSkins::release);
            SKINS.clear();
        });
    }
}
