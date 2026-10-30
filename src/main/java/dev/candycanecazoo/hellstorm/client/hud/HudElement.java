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
 * ({@code textures/gui/hud/element_background.png}, nine-sliced), {@link #paddingX} and {@link #paddingY} round what it
 * shows. An element made {@link #HudElement(String, String, double, double, HudElement) under} another sits just below
 * that one's panel while its {@code attached} setting is on, and uses its own {@code x} and {@code y} when it's off or
 * the other isn't drawn. A subclass adds settings of its own in {@link #defineMore}.
 */
public abstract class HudElement {
    public static final int MARGIN = 8;
    /** Between the panel's edge and what's drawn on it, unless an element says otherwise. */
    public static final int PADDING = 4;
    /** Between an element's panel and one attached under it. */
    public static final int ATTACHED_GAP = 2;
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
    private final HudElement under;
    private ForgeConfigSpec.DoubleValue x;
    private ForgeConfigSpec.DoubleValue y;
    private ForgeConfigSpec.BooleanValue hidden;
    private ForgeConfigSpec.BooleanValue attached;
    /** Where this element's panel was drawn this frame, if it was. Elements are drawn in order, so one attached under it reads it after. */
    private Panel drawn;

    /** A panel on screen. */
    record Panel(int x, int y, int width, int height) {}

    protected HudElement(String id, String description, double defaultX, double defaultY) {
        this(id, description, defaultX, defaultY, null);
    }

    /** An element that sits under {@code under}'s panel, drawn before it. */
    protected HudElement(String id, String description, double defaultX, double defaultY, HudElement under) {
        this.id = id;
        this.description = description;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.under = under;
    }

    protected int paddingX() {
        return PADDING;
    }

    protected int paddingY() {
        return PADDING;
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
        if (under != null) {
            attached = builder.comment("Sit just under the " + under.id + " element, wherever it is. Off uses x and y.")
                    .define("attached", true);
        }
        defineMore(builder);
        builder.pop();
    }

    /** This element's own settings, in its section. */
    protected void defineMore(ForgeConfigSpec.Builder builder) {}

    protected abstract Size size(Frame frame);

    /** Draws the element with its box's top left corner at {@code left}, {@code top}. */
    protected abstract void draw(Frame frame, int left, int top, Size size);

    public final void render(Frame frame) {
        drawn = null;
        if (hidden.get()) {
            return;
        }
        Size size = size(frame);
        if (size.isEmpty()) {
            return;
        }
        int panelWidth = size.width() + 2 * paddingX();
        int panelHeight = size.height() + 2 * paddingY();
        Panel panel = under != null && attached.get() && under.drawn != null
                ? attachedUnder(under.drawn, under.x.get(), panelWidth, panelHeight, frame.width())
                : new Panel(place(x.get(), frame.width(), panelWidth), place(y.get(), frame.height(), panelHeight), panelWidth, panelHeight);
        drawBackground(frame.graphics(), panel.x(), panel.y(), panel.width(), panel.height());
        draw(frame, panel.x() + paddingX(), panel.y() + paddingY(), size);
        drawn = panel;
    }

    /**
     * Just under {@code above}, lined up with it the way it's lined up on screen: left edges together when it's against
     * the left, right edges together against the right, centres together in the middle. Kept inside the margin.
     */
    static Panel attachedUnder(Panel above, double aboveX, int width, int height, int screenWidth) {
        int left = above.x() + (int) Math.round(aboveX * (above.width() - width));
        left = Math.max(MARGIN, Math.min(screenWidth - MARGIN - width, left));
        return new Panel(left, above.y() + above.height() + ATTACHED_GAP, width, height);
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
