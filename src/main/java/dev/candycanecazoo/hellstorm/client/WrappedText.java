package dev.candycanecazoo.hellstorm.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * HUD text that always fits: lines longer than {@link #maxWidth} break onto more lines instead of running off the screen.
 * Every interaction text the mod draws (prompts, notices) goes through here.
 */
public final class WrappedText {
    private static final int SIDE_MARGIN = 8;
    /** Long lines are hard to read at a glance even on a wide screen. */
    private static final int READABLE_WIDTH = 320;

    private WrappedText() {}

    /** The widest a line may be on a screen {@code screenWidth} GUI pixels wide. */
    public static int maxWidth(int screenWidth) {
        return Math.max(40, Math.min(READABLE_WIDTH, screenWidth - 2 * SIDE_MARGIN));
    }

    public static List<FormattedCharSequence> lines(Font font, Component text, int screenWidth) {
        return font.split(text, maxWidth(screenWidth));
    }

    /** Draws {@code text} centred on {@code centerX}, its first line at {@code top}. Returns the height used. */
    public static int drawCentered(GuiGraphics graphics, Font font, Component text, int centerX, int top, int screenWidth, int color) {
        List<FormattedCharSequence> lines = lines(font, text, screenWidth);
        int y = top;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, centerX - font.width(line) / 2, y, color);
            y += font.lineHeight + 1;
        }
        return y - top;
    }

    /** Like {@link #drawCentered}, but the last line ends at {@code bottom}, so the text grows upward. */
    public static int drawCenteredAbove(GuiGraphics graphics, Font font, Component text, int centerX, int bottom, int screenWidth, int color) {
        int height = lines(font, text, screenWidth).size() * (font.lineHeight + 1);
        return drawCentered(graphics, font, text, centerX, bottom - height, screenWidth, color);
    }
}
