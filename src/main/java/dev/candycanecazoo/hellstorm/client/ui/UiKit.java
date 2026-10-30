package dev.candycanecazoo.hellstorm.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Hellstorm's UI pieces, each from its own sprite sheet in {@code textures/gui/}: the frame round every screen, the input
 * box that buttons, fields, slots and dropdowns are drawn in, the switch, and the spinner corners of a number field.
 * One sheet pixel is one GUI pixel.
 */
public final class UiKit {
    private static final ResourceLocation FRAME = Hellstorm.id("textures/gui/frame.png");
    private static final ResourceLocation BOX = Hellstorm.id("textures/gui/input_box.png");
    private static final ResourceLocation SWITCH = Hellstorm.id("textures/gui/switch.png");
    private static final ResourceLocation SPINNERS = Hellstorm.id("textures/gui/spinners.png");
    private static final NineSlice FRAME_SLICES = new NineSlice(0, 0, 192, 64, 0);
    /** Each look of the box is 24 pixels square, cut into 8-pixel corners, edges and middle. */
    private static final int BOX_SIZE = 24;
    private static final int BOX_CORNER = 8;
    public static final int SWITCH_SIZE = 16;
    public static final int SPINNER = 8;

    /** A box's row on its sheet. Buttons use {@link #PRESSED} while held; everything else uses it for {@code DISABLED}. */
    public enum Look {
        NORMAL(0), HOVERED(1), DISABLED(2);

        public static final Look PRESSED = DISABLED;

        final int row;

        Look(int row) {
            this.row = row;
        }
    }

    /** A spinner corner's row on its sheet, from the box's state and whether the mouse is on this spinner. */
    public enum SpinnerLook {
        NORMAL, BOX_HOVERED, HOVERED, FOCUSED, FOCUSED_BOX_HOVERED, FOCUSED_HOVERED, DISABLED, DISABLED_FOCUSED;

        public static SpinnerLook of(boolean disabled, boolean focused, boolean boxHovered, boolean spinnerHovered) {
            if (disabled) {
                return focused ? DISABLED_FOCUSED : DISABLED;
            }
            if (focused) {
                return spinnerHovered ? FOCUSED_HOVERED : boxHovered ? FOCUSED_BOX_HOVERED : FOCUSED;
            }
            return spinnerHovered ? HOVERED : boxHovered ? BOX_HOVERED : NORMAL;
        }
    }

    /** Behind every screen: 90% black over the world. */
    public static final int TINT = 0xE6000000;
    /** The switch's green, for what's chosen. */
    public static final int ACCENT = 0x37946E;
    public static final int TEXT = 0xFFFFFF;

    private UiKit() {}

    public static void tint(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, TINT);
    }

    public static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, FRAME, 192, 192, FRAME_SLICES, x, y, width, height, false);
    }

    /** The input box, its focused column when {@code focused}, its row from {@code look}. */
    public static void box(GuiGraphics graphics, int x, int y, int width, int height, boolean focused, Look look) {
        draw(graphics, BOX, 2 * BOX_SIZE, 3 * BOX_SIZE, boxSlices(focused, look), x, y, width, height, false);
    }

    /**
     * The input box as {@code look}, or while it isn't pressed or disabled, its normal look with the hovered one over it at
     * {@code hover} (0 to 1), so it can ease in and out of hover.
     */
    public static void box(GuiGraphics graphics, int x, int y, int width, int height, boolean focused, Look look, float hover) {
        if (look == Look.DISABLED || hover <= 0) {
            box(graphics, x, y, width, height, focused, look == Look.HOVERED ? Look.NORMAL : look);
            return;
        }
        box(graphics, x, y, width, height, focused, Look.NORMAL);
        graphics.setColor(1, 1, 1, Math.min(1, hover));
        box(graphics, x, y, width, height, focused, Look.HOVERED);
        graphics.setColor(1, 1, 1, 1);
    }

    /**
     * The input box with its bottom left corner the − spinner and its top right the + spinner, each in its own look.
     */
    public static void spinnerBox(GuiGraphics graphics, int x, int y, int width, int height, boolean focused, Look look,
                                  SpinnerLook minus, SpinnerLook plus) {
        draw(graphics, BOX, 2 * BOX_SIZE, 3 * BOX_SIZE, boxSlices(focused, look), x, y, width, height, true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(SPINNERS, x, y + height - SPINNER, 0, minus.ordinal() * SPINNER, SPINNER, SPINNER, 2 * SPINNER, 8 * SPINNER);
        graphics.blit(SPINNERS, x + width - SPINNER, y, SPINNER, plus.ordinal() * SPINNER, SPINNER, SPINNER, 2 * SPINNER, 8 * SPINNER);
        RenderSystem.disableBlend();
    }

    /** The switch: off or on, unfocused or focused, in its row from {@code look}. */
    public static void toggle(GuiGraphics graphics, int x, int y, boolean on, boolean focused, Look look) {
        int column = (on ? 1 : 0) + (focused ? 2 : 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(SWITCH, x, y, column * SWITCH_SIZE, look.row * SWITCH_SIZE, SWITCH_SIZE, SWITCH_SIZE, 4 * SWITCH_SIZE, 3 * SWITCH_SIZE);
        RenderSystem.disableBlend();
    }

    private static NineSlice boxSlices(boolean focused, Look look) {
        return new NineSlice((focused ? 1 : 0) * BOX_SIZE, look.row * BOX_SIZE, BOX_SIZE, BOX_CORNER, 0);
    }

    /** Draws a nine-sliced region; with {@code spinnerCorners} it leaves out the bottom left and top right corners. */
    private static void draw(GuiGraphics graphics, ResourceLocation sheet, int sheetWidth, int sheetHeight, NineSlice slices,
                             int x, int y, int width, int height, boolean spinnerCorners) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (NineSlice.Piece p : slices.pieces(x, y, width, height)) {
            boolean bottomLeft = p.x() == x && p.y() + p.height() == y + height && p.y() > y;
            boolean topRight = p.y() == y && p.x() + p.width() == x + width && p.x() > x;
            if (spinnerCorners && (bottomLeft || topRight)) {
                continue;
            }
            graphics.blit(sheet, p.x(), p.y(), p.width(), p.height(), p.u(), p.v(), p.uWidth(), p.vHeight(), sheetWidth, sheetHeight);
        }
        RenderSystem.disableBlend();
    }
}
