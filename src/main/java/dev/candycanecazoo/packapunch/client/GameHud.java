package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.game.GameWorld;
import net.minecraft.network.chat.Component;
import java.util.List;
import dev.candycanecazoo.packapunch.config.ClientConfig;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.net.HudSyncPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The CoD-style game HUD: the round number large in the bottom left, with the phase under it while it isn't a plain
 * round, and every player's points in the bottom right, your own row in gold with "+N" rising off it as you earn.
 */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GameHud {
    private static final int RED = 0xC81E1E;
    private static final int GOLD = 0xFFD24A;
    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0x8A8A8A;
    private static final int MARGIN = 8;
    private static final float ROUND_SCALE = 3.0f;
    private static final int TIMER_GAP = 12;
    private static final int ENDING_TICKS = 100;
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 5;

    private GameHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "game_hud", (gui, graphics, partialTick, width, height) -> scaled(graphics, width, height, GameHud::draw));
    }

    /** Something drawn against the screen's edges, given the screen size it should lay itself out in. */
    @FunctionalInterface
    public interface Layer {
        void draw(GuiGraphics graphics, int width, int height);
    }

    /**
     * Draws {@code layer} at the player's {@code hud.scale}. The layer sees the screen size divided by the scale, so
     * anything it anchors to an edge stays on that edge.
     */
    public static void scaled(GuiGraphics graphics, int width, int height, Layer layer) {
        float scale = ClientConfig.HUD_SCALE.get().floatValue();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        layer.draw(graphics, Math.round(width / scale), Math.round(height / scale));
        graphics.pose().popPose();
    }

    private static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudState.inGame() || mc.options.hideGui) {
            return;
        }
        HudSyncPacket hud = HudState.hud();
        Font font = mc.font;
        drawRound(graphics, font, hud, height);
        drawPoints(graphics, font, hud, width, height);
        drawTimers(graphics, font, hud, width);
        hud.bar().ifPresent(bar -> drawBar(graphics, font, bar, width, height));
    }

    /** The team's timed effects across the top: "Insta-Kill 23", counting down in seconds. */
    private static void drawTimers(GuiGraphics graphics, Font font, HudSyncPacket hud, int width) {
        if (hud.timers().isEmpty()) {
            return;
        }
        List<String> labels = hud.timers().stream()
                .map(t -> Component.translatable(t.name()).getString() + " " + (t.ticksLeft() + 19) / 20).toList();
        int total = labels.stream().mapToInt(font::width).sum() + TIMER_GAP * (labels.size() - 1);
        int x = (width - total) / 2;
        for (int i = 0; i < labels.size(); i++) {
            GameWorld.Timer timer = hud.timers().get(i);
            boolean ending = timer.ticksLeft() < ENDING_TICKS && (timer.ticksLeft() / 5) % 2 == 0;
            graphics.drawString(font, labels.get(i), x, MARGIN, ending ? GREY : GOLD);
            x += font.width(labels.get(i)) + TIMER_GAP;
        }
    }

    /** This player's progress bar above the crosshair, with its label over it. */
    private static void drawBar(GuiGraphics graphics, Font font, GameWorld.Bar bar, int width, int height) {
        int left = (width - BAR_WIDTH) / 2;
        int top = height / 2 - 28;
        graphics.fill(left - 1, top - 1, left + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, 0xA0000000);
        graphics.fill(left, top, left + Math.round(BAR_WIDTH * Math.max(0, Math.min(1, bar.progress()))), top + BAR_HEIGHT, 0xFF000000 | bar.color());
        WrappedText.drawCenteredAbove(graphics, font, Component.literal(bar.label()), width / 2, top - 2, width, WHITE);
    }

    /** The line under the round number, if any: the phase while it isn't a plain round, else the zombies left. */
    private static java.util.Optional<String> subtitle(HudSyncPacket hud) {
        if (!hud.phase().equals("Round " + hud.round())) {
            return java.util.Optional.of(hud.phase());
        }
        return hud.zombiesLeft() > 0 ? java.util.Optional.of(hud.zombiesLeft() + " left") : java.util.Optional.empty();
    }

    /** The top of the bottom-left round block, so other bottom-left HUD rows (perks) can sit above it. */
    public static int roundTop(Font font, int height) {
        HudSyncPacket hud = HudState.hud();
        int y = height - MARGIN - font.lineHeight;
        if (subtitle(hud).isPresent()) {
            y -= font.lineHeight + 2;
        }
        return hud.round() > 0 ? (int) (y - font.lineHeight * (ROUND_SCALE - 1)) : y + font.lineHeight;
    }

    private static void drawRound(GuiGraphics graphics, Font font, HudSyncPacket hud, int height) {
        int y = height - MARGIN - font.lineHeight;
        java.util.Optional<String> subtitle = subtitle(hud);
        if (subtitle.isPresent()) {
            boolean plainRound = hud.phase().equals("Round " + hud.round());
            graphics.drawString(font, subtitle.get(), MARGIN, y, plainRound ? GREY : WHITE);
            y -= font.lineHeight + 2;
        }
        if (hud.round() > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(MARGIN, y - font.lineHeight * (ROUND_SCALE - 1), 0);
            graphics.pose().scale(ROUND_SCALE, ROUND_SCALE, 1);
            graphics.drawString(font, String.valueOf(hud.round()), 0, 0, RED);
            graphics.pose().popPose();
        }
    }

    private static void drawPoints(GuiGraphics graphics, Font font, HudSyncPacket hud, int width, int height) {
        int y = height - MARGIN - font.lineHeight * hud.players().size() - 2 * (hud.players().size() - 1);
        for (HudSyncPacket.Line line : hud.players()) {
            String points = String.valueOf(line.points());
            int x = width - MARGIN - font.width(points);
            int color = line.downed() ? RED : !line.alive() ? GREY : line.you() ? GOLD : WHITE;
            graphics.drawString(font, points, x, y, color);
            String name = line.name();
            graphics.drawString(font, name, x - 6 - font.width(name), y, line.alive() ? GREY : 0x555555);
            if (line.you() && ClientConfig.SHOW_POPUPS.get()) {
                drawPopups(graphics, font, x, y);
            }
            y += font.lineHeight + 2;
        }
    }

    private static void drawPopups(GuiGraphics graphics, Font font, int x, int y) {
        long now = Util.getMillis();
        for (HudState.Popup popup : HudState.popups()) {
            float age = (now - popup.at()) / (float) HudState.POPUP_MILLIS;
            int alpha = Math.max(8, (int) (255 * (1 - age)));
            String text = (popup.amount() > 0 ? "+" : "") + popup.amount();
            int color = popup.amount() > 0 ? GOLD : RED;
            graphics.drawString(font, text, x - font.width(text) - 4, (int) (y - 10 - age * 18), (alpha << 24) | color);
        }
    }
}
