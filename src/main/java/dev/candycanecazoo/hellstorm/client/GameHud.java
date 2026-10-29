package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.net.HudSyncPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The CoD-style game HUD: the round number large in the top left, with the zombies left under it (or the phase while it
 * isn't a plain round), and in the bottom left your points, with "+N" rising off them as you earn, over a row per player:
 * their face (red while down, a skeleton's once dead), their name cut to {@link #NAME_LENGTH} letters, and their points.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GameHud {
    private static final int RED = 0xC81E1E;
    private static final int GOLD = 0xFFD24A;
    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0x8A8A8A;
    private static final int MARGIN = 8;
    private static final float ROUND_SCALE = 3.0f;
    private static final int TIMER_GAP = 12;
    /** A timer's item icon, and the space between it and the count. */
    private static final int ICON = 16;
    private static final int ICON_GAP = 2;
    /** Each timer's icon, built once per item rather than every frame. */
    private static final Map<ItemSpec.Plain, ItemStack> ICONS = new HashMap<>();
    private static final int ENDING_TICKS = 100;
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 5;
    /** Names longer than this are cut to it and end in "...". */
    static final int NAME_LENGTH = 6;
    private static final int FACE = 8;
    private static final int FACE_GAP = 3;
    private static final int ROW_GAP = 2;
    /** Between your points and the rows under them. */
    private static final int POINTS_GAP = 4;
    private static final ResourceLocation SKELETON = ResourceLocation.tryBuild("minecraft", "textures/entity/skeleton/skeleton.png");

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
        drawRound(graphics, font, hud);
        drawPlayers(graphics, font, hud, height);
        drawTimers(graphics, font, hud, width);
        hud.bar().ifPresent(bar -> drawBar(graphics, font, bar, width, height));
    }

    /** The team's timed effects across the top: the power-up's item, then "Insta-Kill 23", counting down in seconds. */
    private static void drawTimers(GuiGraphics graphics, Font font, HudSyncPacket hud, int width) {
        if (hud.timers().isEmpty()) {
            return;
        }
        List<String> labels = hud.timers().stream()
                .map(t -> Component.translatable(t.name()).getString() + " " + (t.ticksLeft() + 19) / 20).toList();
        int total = labels.stream().mapToInt(font::width).sum() + (ICON + ICON_GAP) * labels.size() + TIMER_GAP * (labels.size() - 1);
        int x = (width - total) / 2;
        for (int i = 0; i < labels.size(); i++) {
            GameWorld.Timer timer = hud.timers().get(i);
            boolean ending = timer.ticksLeft() < ENDING_TICKS && (timer.ticksLeft() / 5) % 2 == 0;
            graphics.renderItem(ICONS.computeIfAbsent(timer.icon(), icon -> icon.build(0).orElse(ItemStack.EMPTY)), x, MARGIN - (ICON - font.lineHeight) / 2);
            x += ICON + ICON_GAP;
            graphics.drawString(font, labels.get(i), x, MARGIN, ending ? GREY : GOLD);
            x += font.width(labels.get(i)) + TIMER_GAP;
        }
    }

    /**
     * This player's progress bar above the crosshair, with its label over it. It keeps moving at the bar's own rate
     * between updates, which come every few ticks, so a board's bar fills smoothly up to the moment the board goes on.
     */
    private static void drawBar(GuiGraphics graphics, Font font, GameWorld.Bar bar, int width, int height) {
        int left = (width - BAR_WIDTH) / 2;
        int top = height / 2 - 28;
        float progress = Math.max(0, Math.min(1, bar.progress() + bar.perTick() * HudState.ticksSinceUpdate()));
        graphics.fill(left - 1, top - 1, left + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, 0xA0000000);
        graphics.fill(left, top, left + Math.round(BAR_WIDTH * progress), top + BAR_HEIGHT, 0xFF000000 | bar.color());
        WrappedText.drawCenteredAbove(graphics, font, Component.literal(bar.label()), width / 2, top - 2, width, WHITE);
    }

    /** The line under the round number, if any: the phase while it isn't a plain round, else the zombies left. */
    private static java.util.Optional<String> subtitle(HudSyncPacket hud) {
        if (!hud.phase().equals("Round " + hud.round())) {
            return java.util.Optional.of(hud.phase());
        }
        return hud.zombiesLeft() > 0 ? java.util.Optional.of(hud.zombiesLeft() + " left") : java.util.Optional.empty();
    }

    private static void drawRound(GuiGraphics graphics, Font font, HudSyncPacket hud) {
        int y = MARGIN;
        if (hud.round() > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(MARGIN, y, 0);
            graphics.pose().scale(ROUND_SCALE, ROUND_SCALE, 1);
            graphics.drawString(font, String.valueOf(hud.round()), 0, 0, RED);
            graphics.pose().popPose();
            y += Math.round(font.lineHeight * ROUND_SCALE) + 2;
        }
        java.util.Optional<String> subtitle = subtitle(hud);
        if (subtitle.isPresent()) {
            boolean plainRound = hud.phase().equals("Round " + hud.round());
            graphics.drawString(font, subtitle.get(), MARGIN, y, plainRound ? GREY : WHITE);
        }
    }

    /** Your points, then a row for every player under them, in join order, anchored to the bottom left. */
    private static void drawPlayers(GuiGraphics graphics, Font font, HudSyncPacket hud, int height) {
        int rowHeight = font.lineHeight + ROW_GAP;
        int top = height - MARGIN - rowHeight * hud.players().size() + ROW_GAP;
        String points = String.valueOf(hud.points());
        int pointsY = top - POINTS_GAP - font.lineHeight;
        graphics.drawString(font, points, MARGIN, pointsY, GOLD);
        if (ClientConfig.SHOW_POPUPS.get()) {
            drawPopups(graphics, font, MARGIN + font.width(points) + 4, pointsY);
        }
        int nameX = MARGIN + FACE + FACE_GAP;
        int pointsX = nameX + hud.players().stream().mapToInt(line -> font.width(shortName(line.name()))).max().orElse(0) + 6;
        int y = top;
        for (HudSyncPacket.Line line : hud.players()) {
            drawFace(graphics, line, MARGIN, y);
            graphics.drawString(font, shortName(line.name()), nameX, y, line.alive() ? GREY : 0x555555);
            int color = line.downed() ? RED : !line.alive() ? GREY : line.you() ? GOLD : WHITE;
            graphics.drawString(font, String.valueOf(line.points()), pointsX, y, color);
            y += rowHeight;
        }
    }

    /** The player's skin face with its hat layer, tinted red while they're down; a skeleton's face once they're dead. */
    private static void drawFace(GuiGraphics graphics, HudSyncPacket.Line line, int x, int y) {
        if (!line.alive() && !line.downed()) {
            graphics.blit(SKELETON, x, y, FACE, FACE, 8, 8, 8, 8, 64, 32);
            return;
        }
        if (line.downed()) {
            graphics.setColor(1, 0.3f, 0.3f, 1);
        }
        PlayerFaceRenderer.draw(graphics, skin(line.name()), x, y, FACE);
        graphics.setColor(1, 1, 1, 1);
    }

    private static ResourceLocation skin(String name) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
        return info == null ? DefaultPlayerSkin.getDefaultSkin() : info.getSkinLocation();
    }

    /** The first {@link #NAME_LENGTH} letters of a name, and "..." if there were more. */
    static String shortName(String name) {
        return name.length() <= NAME_LENGTH ? name : name.substring(0, NAME_LENGTH) + "...";
    }

    private static void drawPopups(GuiGraphics graphics, Font font, int x, int y) {
        long now = Util.getMillis();
        for (HudState.Popup popup : HudState.popups()) {
            float age = (now - popup.at()) / (float) HudState.POPUP_MILLIS;
            int alpha = Math.max(8, (int) (255 * (1 - age)));
            String text = (popup.amount() > 0 ? "+" : "") + popup.amount();
            int color = popup.amount() > 0 ? GOLD : RED;
            graphics.drawString(font, text, x, (int) (y - 10 - age * 18), (alpha << 24) | color);
        }
    }
}
