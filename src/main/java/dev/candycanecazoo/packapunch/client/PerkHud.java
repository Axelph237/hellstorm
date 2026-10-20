package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.net.PerkSyncPacket;
import dev.candycanecazoo.packapunch.tacz.LocalGunPerks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** A row of perk names in their colors at the bottom left, standing in for perk icons (see TEXTURES_NEEDED.md). */
public final class PerkHud {
    private static final int MARGIN = 4;
    private static final int GAP = 3;

    private PerkHud() {}

    public static void update(PerkSyncPacket packet) {
        boolean rpmChanged = packet.rpmMultiplier() != ClientPerks.rpmMultiplier();
        ClientPerks.update(packet);
        if (rpmChanged) {
            LocalGunPerks.refresh();
        }
    }

    private static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        List<PerkSyncPacket.Badge> badges = ClientPerks.badges();
        if (!HudState.inGame() || badges.isEmpty() || mc.options.hideGui) {
            return;
        }
        int left = MARGIN + 4;
        int right = Math.max(left + 40, width / 2 - 100);
        int x = left;
        int y = GameHud.roundTop(mc.font, height) - 6 - mc.font.lineHeight;
        for (PerkSyncPacket.Badge badge : badges) {
            Component name = Component.translatable(badge.name());
            int w = mc.font.width(name);
            if (x > left && x + w > right) {
                x = left;
                y -= mc.font.lineHeight + 4;
            }
            graphics.fill(x - 2, y - 2, x + w + 2, y + mc.font.lineHeight, 0x90000000);
            graphics.drawString(mc.font, name, x, y, 0xFF000000 | badge.color());
            x += w + 4 + GAP;
        }
    }

    @Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "perks", (gui, graphics, partialTick, width, height) -> draw(graphics, width, height));
        }
    }

    @Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT)
    public static final class Session {
        private Session() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientPerks.update(new PerkSyncPacket(List.of(), 1f));
        }
    }
}
