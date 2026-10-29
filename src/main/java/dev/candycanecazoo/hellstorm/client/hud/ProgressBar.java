package dev.candycanecazoo.hellstorm.client.hud;

import dev.candycanecazoo.hellstorm.client.HudState;
import dev.candycanecazoo.hellstorm.client.WrappedText;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import net.minecraft.network.chat.Component;

/**
 * This player's progress bar (reviving, being revived, bleeding out, rebuilding), with its label over it. It keeps moving
 * at the bar's own rate between updates, which come every few ticks, so a board's bar fills smoothly up to the moment the
 * board goes on. By default it sits just above the crosshair.
 */
public final class ProgressBar extends HudElement {
    private static final int WIDTH = 100;
    private static final int HEIGHT = 5;
    private static final int LABEL_GAP = 2;

    ProgressBar() {
        super("bar", "The progress bar for reviving, bleeding out and rebuilding.", 0.5, 0.4);
    }

    @Override
    protected Size size(Frame frame) {
        if (frame.hud().bar().isEmpty()) {
            return Size.NONE;
        }
        var lines = WrappedText.lines(frame.font(), label(frame.hud().bar().get()), frame.width());
        int width = lines.stream().mapToInt(frame.font()::width).max().orElse(0);
        return new Size(Math.max(WIDTH + 2, width), lines.size() * frame.font().lineHeight + LABEL_GAP + HEIGHT + 1);
    }

    @Override
    protected void draw(Frame frame, int left, int top, Size size) {
        GameWorld.Bar bar = frame.hud().bar().orElseThrow();
        int center = left + size.width() / 2;
        int barLeft = center - WIDTH / 2;
        int barTop = top + size.height() - HEIGHT - 1;
        float progress = Math.max(0, Math.min(1, bar.progress() + bar.perTick() * HudState.ticksSinceUpdate()));
        frame.graphics().fill(barLeft - 1, barTop - 1, barLeft + WIDTH + 1, barTop + HEIGHT + 1, 0xA0000000);
        frame.graphics().fill(barLeft, barTop, barLeft + Math.round(WIDTH * progress), barTop + HEIGHT, 0xFF000000 | bar.color());
        WrappedText.drawCenteredAbove(frame.graphics(), frame.font(), label(bar), center, barTop - LABEL_GAP, frame.width(), Colors.WHITE);
    }

    private static Component label(GameWorld.Bar bar) {
        return Component.literal(bar.label());
    }
}
