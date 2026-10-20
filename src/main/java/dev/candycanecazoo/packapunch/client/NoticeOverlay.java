package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The mod's action-bar messages (refusals, tool feedback), wrapped to fit, shown where the action bar is and faded out. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NoticeOverlay {
    private static final long SHOW_MILLIS = 3_000;
    private static final long FADE_MILLIS = 500;
    /** The bottom of the vanilla action bar's line, above the hotbar and the health and armour rows. */
    private static final int ABOVE_BOTTOM = 59;

    private static Component message = Component.empty();
    private static long shownAt = Long.MIN_VALUE / 2;

    private NoticeOverlay() {}

    public static void show(Component next) {
        message = next;
        shownAt = Util.getMillis();
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.RECORD_OVERLAY.id(), "notice", (gui, graphics, partialTick, width, height) -> draw(graphics, width, height));
    }

    private static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        long age = Util.getMillis() - shownAt;
        if (age > SHOW_MILLIS || mc.options.hideGui) {
            return;
        }
        int alpha = age < SHOW_MILLIS - FADE_MILLIS ? 255 : (int) (255 * (SHOW_MILLIS - age) / (double) FADE_MILLIS);
        WrappedText.drawCenteredAbove(graphics, mc.font, message, width / 2, height - ABOVE_BOTTOM, width, (Math.max(alpha, 8) << 24) | 0xFFFFFF);
    }
}
