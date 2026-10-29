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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
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
 * isn't a plain round), and a row per player where {@code hud.player_list} puts it (the middle of the right side by
 * default): their face (red while down, a skeleton's once dead), their name cut to {@link #NAME_LENGTH} letters, and their
 * points, with "+N" rising off yours as you earn. On the right a row reads the other way, so the face is at the edge.
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
    private static final int COLUMN_GAP = 6;
    /** Vanilla's effect icons in the top right: one row for good effects, a second below for bad ones. */
    private static final int EFFECT_ROW = 26;
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
        int roundBottom = drawRound(graphics, font, hud);
        drawPlayers(graphics, font, hud, width, height, roundBottom);
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

    /** Returns the bottom of what it drew. */
    private static int drawRound(GuiGraphics graphics, Font font, HudSyncPacket hud) {
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
            y += font.lineHeight + 2;
        }
        return y;
    }

    /**
     * A row for every player, in join order, where {@code hud.player_list} puts them. At the top they keep clear of the
     * round on the left and of vanilla's effect icons on the right.
     */
    private static void drawPlayers(GuiGraphics graphics, Font font, HudSyncPacket hud, int width, int height, int roundBottom) {
        ClientConfig.PlayerListPosition position = ClientConfig.PLAYER_LIST.get();
        int rowHeight = font.lineHeight + ROW_GAP;
        int block = rowHeight * hud.players().size() - ROW_GAP;
        int clearTop = position.right ? effectIconsBottom() : roundBottom;
        int y = position.top(height, block, MARGIN, clearTop);
        int names = hud.players().stream().mapToInt(line -> font.width(shortName(line.name()))).max().orElse(0);
        for (HudSyncPacket.Line line : hud.players()) {
            String name = shortName(line.name());
            String points = String.valueOf(line.points());
            int color = line.downed() ? RED : !line.alive() ? GREY : line.you() ? GOLD : WHITE;
            int nameColor = line.alive() ? GREY : 0x555555;
            boolean popups = line.you() && ClientConfig.SHOW_POPUPS.get();
            if (position.right) {
                int face = width - MARGIN - FACE;
                int nameRight = face - FACE_GAP;
                int pointsRight = nameRight - names - COLUMN_GAP;
                drawFace(graphics, line, face, y);
                graphics.drawString(font, name, nameRight - font.width(name), y, nameColor);
                graphics.drawString(font, points, pointsRight - font.width(points), y, color);
                if (popups) {
                    drawPopups(graphics, font, pointsRight - font.width(points) - 4, y, true);
                }
            } else {
                int nameX = MARGIN + FACE + FACE_GAP;
                int pointsX = nameX + names + COLUMN_GAP;
                drawFace(graphics, line, MARGIN, y);
                graphics.drawString(font, name, nameX, y, nameColor);
                graphics.drawString(font, points, pointsX, y, color);
                if (popups) {
                    drawPopups(graphics, font, pointsX + font.width(points) + 4, y, false);
                }
            }
            y += rowHeight;
        }
    }

    /** How far down vanilla's effect icons reach in this layer's scaled space: 0 with no effects shown. */
    private static int effectIconsBottom() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        boolean good = false;
        boolean bad = false;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.showIcon()) {
                good |= effect.getEffect().isBeneficial();
                bad |= !effect.getEffect().isBeneficial();
            }
        }
        int rows = bad ? 2 : good ? 1 : 0;
        return rows == 0 ? 0 : Math.round((rows * EFFECT_ROW + ROW_GAP) / ClientConfig.HUD_SCALE.get().floatValue());
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

    /** The +N / -N rising from {@code x}: to its left when {@code leftOf}, else to its right. */
    private static void drawPopups(GuiGraphics graphics, Font font, int x, int y, boolean leftOf) {
        long now = Util.getMillis();
        for (HudState.Popup popup : HudState.popups()) {
            float age = (now - popup.at()) / (float) HudState.POPUP_MILLIS;
            int alpha = Math.max(8, (int) (255 * (1 - age)));
            String text = (popup.amount() > 0 ? "+" : "") + popup.amount();
            int color = popup.amount() > 0 ? GOLD : RED;
            graphics.drawString(font, text, leftOf ? x - font.width(text) : x, (int) (y - 10 - age * 18), (alpha << 24) | color);
        }
    }
}
