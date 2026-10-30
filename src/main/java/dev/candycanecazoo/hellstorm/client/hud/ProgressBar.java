package dev.candycanecazoo.hellstorm.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.HudState;
import dev.candycanecazoo.hellstorm.client.WrappedText;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * This player's progress bar (reviving, being revived, bleeding out, rebuilding), with its label over it, drawn as the
 * loader ({@code textures/gui/hud/loader.png}): its top half the empty bar, its bottom half the full one, each stretched
 * to {@link #WIDTH} with {@link #END}-pixel ends kept as drawn. The full bar covers the empty one up to the progress, in
 * the bar's color. It keeps moving at the bar's own rate between updates, which come every few ticks, so a board's bar
 * fills smoothly up to the moment the board goes on. No panel behind it. By default it sits just above the crosshair.
 */
public final class ProgressBar extends HudElement {
    private static final ResourceLocation LOADER = Hellstorm.id("textures/gui/hud/loader.png");
    private static final int SHEET_WIDTH = 24;
    private static final int SHEET_HEIGHT = 16;
    private static final int HEIGHT = 8;
    private static final int END = 3;
    private static final int WIDTH = 100;
    private static final int LABEL_GAP = 2;

    /** Where a piece of the loader's row lands, and the part of the sheet it shows. */
    record Piece(int x, int width, int u, int uWidth) {}

    ProgressBar() {
        super("bar", "The progress bar for reviving, bleeding out and rebuilding.", 0.5, 0.4);
    }

    @Override
    protected boolean background() {
        return false;
    }

    @Override
    protected int paddingX() {
        return 0;
    }

    @Override
    protected int paddingY() {
        return 0;
    }

    @Override
    protected Size size(Frame frame) {
        if (frame.hud().bar().isEmpty()) {
            return Size.NONE;
        }
        var lines = WrappedText.lines(frame.font(), label(frame.hud().bar().get()), frame.width());
        int width = lines.stream().mapToInt(frame.font()::width).max().orElse(0);
        return new Size(Math.max(WIDTH, width), lines.size() * frame.font().lineHeight + LABEL_GAP + HEIGHT);
    }

    @Override
    protected void draw(Frame frame, int left, int top, Size size) {
        GameWorld.Bar bar = frame.hud().bar().orElseThrow();
        GuiGraphics graphics = frame.graphics();
        int center = left + size.width() / 2;
        int barLeft = center - WIDTH / 2;
        int barTop = top + size.height() - HEIGHT;
        float progress = Math.max(0, Math.min(1, bar.progress() + bar.perTick() * HudState.ticksSinceUpdate()));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawRow(graphics, pieces(barLeft, WIDTH, WIDTH), barTop, 0);
        graphics.setColor((bar.color() >> 16 & 0xff) / 255f, (bar.color() >> 8 & 0xff) / 255f, (bar.color() & 0xff) / 255f, 1);
        drawRow(graphics, pieces(barLeft, WIDTH, Math.round(WIDTH * progress)), barTop, HEIGHT);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        WrappedText.drawCenteredAbove(graphics, frame.font(), label(bar), center, barTop - LABEL_GAP, frame.width(), Colors.WHITE);
    }

    private static void drawRow(GuiGraphics graphics, List<Piece> pieces, int y, int v) {
        for (Piece p : pieces) {
            graphics.blit(LOADER, p.x(), y, p.width(), HEIGHT, p.u(), v, p.uWidth(), HEIGHT, SHEET_WIDTH, SHEET_HEIGHT);
        }
    }

    /**
     * A row {@code width} wide from {@code x}, cut into its two ends and the stretched middle, showing only its first
     * {@code shown} pixels.
     */
    static List<Piece> pieces(int x, int width, int shown) {
        int[] xs = {x, x + END, x + width - END};
        int[] widths = {END, width - 2 * END, END};
        int[] us = {0, END, SHEET_WIDTH - END};
        int[] uWidths = {END, SHEET_WIDTH - 2 * END, END};
        List<Piece> pieces = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            int visible = Math.max(0, Math.min(widths[i], shown - (xs[i] - x)));
            if (visible > 0) {
                pieces.add(new Piece(xs[i], visible, us[i], Math.max(1, uWidths[i] * visible / widths[i])));
            }
        }
        return pieces;
    }

    private static Component label(GameWorld.Bar bar) {
        return Component.literal(bar.label());
    }
}
