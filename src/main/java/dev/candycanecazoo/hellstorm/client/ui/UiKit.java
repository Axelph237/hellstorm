package dev.candycanecazoo.hellstorm.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Hellstorm's UI pieces, from two sprite sheets: {@code gui/frame.png} (the frame around every screen) and
 * {@code gui/inputs.png} (the toggle, the spinner's halves, the search icon, and the box that buttons, fields, slots
 * and dropdowns are drawn in). Coordinates are in each sheet's pixels; one sheet pixel is one GUI pixel.
 */
public final class UiKit {
    private static final ResourceLocation FRAME = Hellstorm.id("textures/gui/frame.png");
    private static final ResourceLocation INPUTS = Hellstorm.id("textures/gui/inputs.png");
    private static final int FRAME_SHEET = 192;
    private static final int INPUTS_SHEET = 128;
    private static final NineSlice FRAME_SLICES = new NineSlice(0, 0, 192, 64, 0);
    /** On the sheet the box's 12-pixel corners and its edge pieces stand 4 pixels apart. */
    private static final NineSlice BOX_SLICES = new NineSlice(82, 82, 44, 12, 4);

    public record Sprite(int u, int v, int width, int height) {}

    public static final Sprite TOGGLE_OFF = new Sprite(66, 9, 28, 14);
    public static final Sprite TOGGLE_ON = new Sprite(98, 9, 28, 14);
    public static final Sprite SPIN_UP = new Sprite(106, 33, 10, 14);
    public static final Sprite SPIN_DOWN = new Sprite(106, 49, 10, 14);
    public static final Sprite SEARCH = new Sprite(119, 74, 7, 7);

    /** Behind every screen: 80% black over the world. */
    public static final int TINT = 0xCC000000;
    /** Stands in for hover art until there is some: a faint lift inside the box. */
    public static final int HOVER = 0x26FFFFFF;
    /** The toggle's green, for what's chosen. */
    public static final int ACCENT = 0x37946E;
    public static final int TEXT = 0xFFFFFF;

    private UiKit() {}

    public static void tint(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, TINT);
    }

    public static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, FRAME, FRAME_SHEET, FRAME_SLICES, x, y, width, height);
    }

    public static void box(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, INPUTS, INPUTS_SHEET, BOX_SLICES, x, y, width, height);
    }

    public static void hover(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, HOVER);
    }

    public static void sprite(GuiGraphics graphics, Sprite sprite, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(INPUTS, x, y, sprite.u(), sprite.v(), sprite.width(), sprite.height(), INPUTS_SHEET, INPUTS_SHEET);
        RenderSystem.disableBlend();
    }

    private static void draw(GuiGraphics graphics, ResourceLocation sheet, int sheetSize, NineSlice slices, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (NineSlice.Piece p : slices.pieces(x, y, width, height)) {
            graphics.blit(sheet, p.x(), p.y(), p.width(), p.height(), p.u(), p.v(), p.uWidth(), p.vHeight(), sheetSize, sheetSize);
        }
        RenderSystem.disableBlend();
    }
}
