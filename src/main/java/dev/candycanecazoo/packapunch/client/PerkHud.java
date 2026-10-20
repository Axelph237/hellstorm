package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.perk.Perks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;

/**
 * The local player's perks (active effects in {@code packapunch:perks}) as names in their colors at the bottom left,
 * standing in for perk icons (see TEXTURES_NEEDED.md).
 */
public final class PerkHud {
    private static final int MARGIN = 4;
    private static final int GAP = 3;

    private PerkHud() {}

    private static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudState.inGame() || mc.player == null || mc.options.hideGui) {
            return;
        }
        List<MobEffect> perks = Perks.held(mc.player).stream()
                .sorted(Comparator.comparing(effect -> String.valueOf(ForgeRegistries.MOB_EFFECTS.getKey(effect)))).toList();
        int left = MARGIN + 4;
        int right = Math.max(left + 40, width / 2 - 100);
        int x = left;
        int y = GameHud.roundTop(mc.font, height) - 6 - mc.font.lineHeight;
        for (MobEffect perk : perks) {
            Component name = perk.getDisplayName();
            int w = mc.font.width(name);
            if (x > left && x + w > right) {
                x = left;
                y -= mc.font.lineHeight + 4;
            }
            graphics.fill(x - 2, y - 2, x + w + 2, y + mc.font.lineHeight, 0x90000000);
            graphics.drawString(mc.font, name, x, y, 0xFF000000 | perk.getColor());
            x += w + 4 + GAP;
        }
    }

    @Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "perks", (gui, graphics, partialTick, width, height) -> GameHud.scaled(graphics, width, height, PerkHud::draw));
        }
    }
}
