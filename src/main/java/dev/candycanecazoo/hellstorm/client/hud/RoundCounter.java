package dev.candycanecazoo.hellstorm.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.net.HudSyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The round number large in Roman numerals ({@link RomanNumerals}), in red digits past what the numerals reach, with the
 * zombies left under it, or the phase while it isn't a plain round.
 */
public final class RoundCounter extends HudElement {
    private static final int SCALE = 3;
    private static final int LINE_GAP = 2;
    private static final Map<RomanNumerals.Glyph, ResourceLocation> GLYPHS = new EnumMap<>(RomanNumerals.Glyph.class);

    static {
        for (RomanNumerals.Glyph glyph : RomanNumerals.Glyph.values()) {
            GLYPHS.put(glyph, Hellstorm.id("textures/gui/hud/numeral_" + glyph.value + ".png"));
        }
    }

    RoundCounter() {
        super("round", "The round number and the zombies left.", 0, 0);
    }

    @Override
    protected Size size(Frame frame) {
        int width = 0;
        int height = 0;
        if (frame.hud().round() > 0) {
            width = numberWidth(frame);
            height = RomanNumerals.HEIGHT * SCALE;
        }
        Optional<String> subtitle = subtitle(frame.hud());
        if (subtitle.isPresent()) {
            width = Math.max(width, frame.font().width(subtitle.get()));
            height += (height > 0 ? LINE_GAP : 0) + frame.font().lineHeight;
        }
        return new Size(width, height);
    }

    private static int numberWidth(Frame frame) {
        int round = frame.hud().round();
        return RomanNumerals.glyphs(round).map(glyphs -> RomanNumerals.layout(glyphs).width())
                .orElseGet(() -> frame.font().width(String.valueOf(round))) * SCALE;
    }

    @Override
    protected void draw(Frame frame, int left, int top, Size size) {
        HudSyncPacket hud = frame.hud();
        GuiGraphics graphics = frame.graphics();
        if (hud.round() > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(left, top, 0);
            graphics.pose().scale(SCALE, SCALE, 1);
            Optional<List<RomanNumerals.Glyph>> glyphs = RomanNumerals.glyphs(hud.round());
            if (glyphs.isPresent()) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                for (RomanNumerals.Placed placed : RomanNumerals.layout(glyphs.get()).glyphs()) {
                    int width = placed.glyph().width;
                    graphics.blit(GLYPHS.get(placed.glyph()), placed.x(), 0, 0, 0, width, RomanNumerals.HEIGHT, width, RomanNumerals.HEIGHT);
                }
                RenderSystem.disableBlend();
            } else {
                graphics.drawString(frame.font(), String.valueOf(hud.round()), 0, 0, Colors.RED);
            }
            graphics.pose().popPose();
        }
        boolean plainRound = hud.phase().equals("Round " + hud.round());
        subtitle(hud).ifPresent(text -> graphics.drawString(frame.font(), text, left, top + size.height() - frame.font().lineHeight,
                plainRound ? Colors.GREY : Colors.WHITE));
    }

    /** The line under the round number, if any: the phase while it isn't a plain round, else the zombies left. */
    private static Optional<String> subtitle(HudSyncPacket hud) {
        if (!hud.phase().equals("Round " + hud.round())) {
            return Optional.of(hud.phase());
        }
        return hud.zombiesLeft() > 0 ? Optional.of(hud.zombiesLeft() + " left") : Optional.empty();
    }
}
