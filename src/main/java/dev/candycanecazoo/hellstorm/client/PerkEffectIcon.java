package dev.candycanecazoo.hellstorm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/**
 * A perk effect's icon as a square in the effect's color with the perk's initial, standing in for the icon textures in
 * TEXTURES_NEEDED.md so nothing draws as a missing texture.
 */
public final class PerkEffectIcon implements IClientMobEffectExtensions {
    private static final int SIZE = 18;

    @Override
    public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen, GuiGraphics graphics, int x, int y, int blitOffset) {
        draw(graphics, instance.getEffect(), x, y + 7);
        return true;
    }

    @Override
    public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics, int x, int y, float z, float alpha) {
        draw(graphics, instance.getEffect(), x + 3, y + 3);
        return true;
    }

    /** Where vanilla would blit the 18 by 18 sprite. */
    private static void draw(GuiGraphics graphics, MobEffect effect, int x, int y) {
        graphics.fill(x, y, x + SIZE, y + SIZE, 0xFF000000 | effect.getColor());
        Font font = Minecraft.getInstance().font;
        String name = effect.getDisplayName().getString();
        String initial = name.isEmpty() ? "?" : name.substring(0, 1);
        graphics.drawString(font, initial, x + (SIZE - font.width(initial) + 1) / 2, y + (SIZE - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
    }
}
