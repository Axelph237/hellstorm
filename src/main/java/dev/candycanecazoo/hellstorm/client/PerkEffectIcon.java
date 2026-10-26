package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/** A perk effect's icon, one 16 by 16 cell of the perk icon sheet, read left to right and top to bottom. */
public final class PerkEffectIcon implements IClientMobEffectExtensions {
    private static final ResourceLocation SHEET = Hellstorm.id("textures/gui/perk_icons.png");
    private static final int SHEET_SIZE = 64;
    private static final int ICON = 16;
    private static final int COLUMNS = SHEET_SIZE / ICON;

    private final int cell;

    public PerkEffectIcon(int cell) {
        this.cell = cell;
    }

    @Override
    public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen, GuiGraphics graphics, int x, int y, int blitOffset) {
        draw(graphics, x, y + 7);
        return true;
    }

    @Override
    public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics, int x, int y, float z, float alpha) {
        graphics.setColor(1, 1, 1, alpha);
        draw(graphics, x + 3, y + 3);
        graphics.setColor(1, 1, 1, 1);
        return true;
    }

    /** Centred where vanilla would blit its 18 by 18 sprite. */
    private void draw(GuiGraphics graphics, int x, int y) {
        int u = cell % COLUMNS * ICON;
        int v = cell / COLUMNS * ICON;
        graphics.blit(SHEET, x + 1, y + 1, u, v, ICON, ICON, SHEET_SIZE, SHEET_SIZE);
    }
}
