package dev.candycanecazoo.hellstorm.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The round number large in Roman numerals ({@link RomanNumerals}), in red digits past what the numerals reach. */
public final class RoundCounter extends HudElement {
    private static final float SCALE = 1.8f;
    /** More room beside the numerals than above and below them. */
    private static final int PADDING_X = 7;
    private static final Map<RomanNumerals.Glyph, ResourceLocation> GLYPHS = new EnumMap<>(RomanNumerals.Glyph.class);

    static {
        for (RomanNumerals.Glyph glyph : RomanNumerals.Glyph.values()) {
            GLYPHS.put(glyph, Hellstorm.id("textures/gui/hud/numeral_" + glyph.value + ".png"));
        }
    }

    RoundCounter() {
        super("round", "The round number.", 0, 0);
    }

    @Override
    protected int paddingX() {
        return PADDING_X;
    }

    @Override
    protected Size size(Frame frame) {
        if (frame.hud().round() <= 0) {
            return Size.NONE;
        }
        return new Size(numberWidth(frame), Math.round(RomanNumerals.HEIGHT * SCALE));
    }

    private static int numberWidth(Frame frame) {
        int round = frame.hud().round();
        return RomanNumerals.glyphs(round).map(glyphs -> RomanNumerals.layout(glyphs).width())
                .map(width -> Math.round(width * SCALE)).orElseGet(() -> Math.round(frame.font().width(String.valueOf(round)) * SCALE));
    }

    @Override
    protected void draw(Frame frame, int left, int top, Size size) {
        int round = frame.hud().round();
        GuiGraphics graphics = frame.graphics();
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        Optional<List<RomanNumerals.Glyph>> glyphs = RomanNumerals.glyphs(round);
        if (glyphs.isPresent()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (RomanNumerals.Placed placed : RomanNumerals.layout(glyphs.get()).glyphs()) {
                int width = placed.glyph().width;
                graphics.blit(GLYPHS.get(placed.glyph()), placed.x(), 0, 0, 0, width, RomanNumerals.HEIGHT, width, RomanNumerals.HEIGHT);
            }
            RenderSystem.disableBlend();
        } else {
            graphics.drawString(frame.font(), String.valueOf(round), 0, 0, Colors.RED);
        }
        graphics.pose().popPose();
    }
}
