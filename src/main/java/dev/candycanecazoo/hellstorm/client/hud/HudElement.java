package dev.candycanecazoo.hellstorm.client.hud;

import dev.candycanecazoo.hellstorm.net.HudSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Something the game HUD draws, with its own section of the client config, {@code [hud.<id>]}: where it sits and whether
 * it's hidden. {@code x} and {@code y} run from 0 to 1 across the screen inside its margin, and say where the element's
 * box sits in the room left over: 0 against the left or top edge, 1 against the right or bottom, 0.5 centred. So an
 * element is never off the screen, at any size or GUI scale. A subclass adds settings of its own in {@link #defineMore}.
 */
public abstract class HudElement {
    public static final int MARGIN = 8;

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
        draw(frame, place(x.get(), frame.width(), size.width()), place(y.get(), frame.height(), size.height()), size);
    }

    /** Where a box {@code size} long starts along a screen {@code screen} long, {@code fraction} of the way through the room it has. */
    static int place(double fraction, int screen, int size) {
        return MARGIN + (int) Math.round(fraction * Math.max(0, screen - 2 * MARGIN - size));
    }
}
