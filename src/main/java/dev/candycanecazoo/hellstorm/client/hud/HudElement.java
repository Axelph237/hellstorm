package dev.candycanecazoo.hellstorm.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.ui.NineSlice;
import dev.candycanecazoo.hellstorm.net.HudSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Something the game HUD draws, with its own section of the client config, {@code [hud.<id>]}: where it sits and whether
 * it's hidden. {@code x} and {@code y} run from 0 to 1 across the screen inside its margin, and say where the element's
 * box sits in the room left over: 0 against the left or top edge, 1 against the right or bottom, 0.5 centred. So an
 * element is never off the screen, at any size or GUI scale. Each is drawn on the HUD's rounded panel
 * ({@code textures/gui/hud/element_background.png}, nine-sliced), {@link #PADDING} round what it shows. A subclass adds
 * settings of its own in {@link #defineMore}.
 */
public abstract class HudElement {
    public static final int MARGIN = 8;
    /** Between the panel's edge and what's drawn on it. */
    public static final int PADDING = 4;
    private static final ResourceLocation BACKGROUND = Hellstorm.id("textures/gui/hud/element_background.png");
    private static final int BACKGROUND_SIZE = 9;
    private static final NineSlice BACKGROUND_SLICES = new NineSlice(0, 0, BACKGROUND_SIZE, 3, 0);

    /** What an element draws from, in the HUD's scaled GUI pixels. */
    public record Frame(GuiGraphics graphics, Font font, HudSyncPacket hud, int width, int height) {}

    /** An element's box this frame. Empty when it has nothing to show. */
    public record Size(int width, int height) {
        public static final Size NONE = new Size(0, 0);

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }

    private final String id;
    private final String description;
    private final double defaultX;
    private final double defaultY;
    private ForgeConfigSpec.DoubleValue x;
    private ForgeConfigSpec.DoubleValue y;
    private ForgeConfigSpec.BooleanValue hidden;

    protected HudElement(String id, String description, double defaultX, double defaultY) {
        this.id = id;
        this.description = description;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
    }

    public String id() {
        return id;
    }

    /** Adds this element's section to the client config. */
    public final void define(ForgeConfigSpec.Builder builder) {
        builder.comment(description).push(id);
        x = builder.comment("Across the screen: 0 against the left edge, 1 against the right, 0.5 centred.")
                .defineInRange("x", defaultX, 0.0, 1.0);
        y = builder.comment("Down the screen: 0 against the top edge, 1 against the bottom, 0.5 centred.")
                .defineInRange("y", defaultY, 0.0, 1.0);
        hidden = builder.comment("Don't draw it.").define("hidden", false);
        defineMore(builder);
        builder.pop();
    }

    /** This element's own settings, in its section. */
    protected void defineMore(ForgeConfigSpec.Builder builder) {}

    protected abstract Size size(Frame frame);

    /** Draws the element with its box's top left corner at {@code left}, {@code top}. */
    protected abstract void draw(Frame frame, int left, int top, Size size);

    public final void render(Frame frame) {
        if (hidden.get()) {
            return;
        }
        Size size = size(frame);
        if (size.isEmpty()) {
            return;
        }
        int panelWidth = size.width() + 2 * PADDING;
        int panelHeight = size.height() + 2 * PADDING;
        int left = place(x.get(), frame.width(), panelWidth);
        int top = place(y.get(), frame.height(), panelHeight);
        drawBackground(frame.graphics(), left, top, panelWidth, panelHeight);
        draw(frame, left + PADDING, top + PADDING, size);
    }

    private static void drawBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (NineSlice.Piece p : BACKGROUND_SLICES.pieces(x, y, width, height)) {
            graphics.blit(BACKGROUND, p.x(), p.y(), p.width(), p.height(), p.u(), p.v(), p.uWidth(), p.vHeight(), BACKGROUND_SIZE, BACKGROUND_SIZE);
        }
        RenderSystem.disableBlend();
    }

    /** Where a box {@code size} long starts along a screen {@code screen} long, {@code fraction} of the way through the room it has. */
    static int place(double fraction, int screen, int size) {
        return MARGIN + (int) Math.round(fraction * Math.max(0, screen - 2 * MARGIN - size));
    }
}
