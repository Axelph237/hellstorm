package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.hud.HudElement;
import dev.candycanecazoo.hellstorm.client.hud.HudElements;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The CoD-style game HUD: every {@link HudElements} element, each placed and hidden by its own section of the client
 * config, all sized by {@code hud.scale}.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GameHud {
    private GameHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "game_hud", (gui, graphics, partialTick, width, height) -> draw(graphics, width, height));
    }

    /**
     * Draws at the player's {@code hud.scale}. The elements see the screen size divided by the scale, so one placed
     * against an edge stays on that edge.
     */
    private static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudState.inGame() || mc.options.hideGui) {
            return;
        }
        float scale = ClientConfig.HUD_SCALE.get().floatValue();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        HudElement.Frame frame = new HudElement.Frame(graphics, mc.font, HudState.hud(), Math.round(width / scale), Math.round(height / scale));
        HudElements.ALL.forEach(element -> element.render(frame));
        graphics.pose().popPose();
    }
}
