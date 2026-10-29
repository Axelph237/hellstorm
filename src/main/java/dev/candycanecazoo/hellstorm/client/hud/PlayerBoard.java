package dev.candycanecazoo.hellstorm.client.hud;

import dev.candycanecazoo.hellstorm.client.HudState;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
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
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * A row for every player, in join order: their face (red while down, a skeleton's once dead), their name cut to
 * {@link #NAME_LENGTH} letters, and their points as "$1500", with "+N" rising off yours as you earn. {@code justify} sets the order:
 * {@code LEFT} reads face, name, points against the box's left side, {@code RIGHT} points, name, face against its right.
 */
public final class PlayerBoard extends HudElement {
    public enum Justify { LEFT, RIGHT }

    /** Names longer than this are cut to it and end in "...". */
    static final int NAME_LENGTH = 6;
    private static final int FACE = 8;
    private static final int FACE_GAP = 3;
    private static final int ROW_GAP = 2;
    private static final int COLUMN_GAP = 6;
    private static final int POPUP_GAP = 4;
    private static final ResourceLocation SKELETON = ResourceLocation.tryBuild("minecraft", "textures/entity/skeleton/skeleton.png");

    private ForgeConfigSpec.EnumValue<Justify> justify;

    PlayerBoard() {
        super("players", "The rows of players: face, name and points.", 1, 0.5);
    }

    @Override
    protected void defineMore(ForgeConfigSpec.Builder builder) {
        justify = builder.comment("LEFT reads face, name, points; RIGHT reads points, name, face, with the face at the right.")
                .defineEnum("justify", Justify.RIGHT);
    }

    @Override
    protected Size size(Frame frame) {
        int rows = frame.hud().players().size();
        if (rows == 0) {
            return Size.NONE;
        }
        int points = frame.hud().players().stream().mapToInt(line -> frame.font().width(points(line))).max().orElse(0);
        int width = FACE + FACE_GAP + names(frame) + COLUMN_GAP + points;
        return new Size(width, (frame.font().lineHeight + ROW_GAP) * rows - ROW_GAP);
    }

    @Override
    protected void draw(Frame frame, int left, int top, Size size) {
        Font font = frame.font();
        GuiGraphics graphics = frame.graphics();
        boolean right = justify.get() == Justify.RIGHT;
        int names = names(frame);
        int y = top;
        for (HudSyncPacket.Line line : frame.hud().players()) {
            String name = shortName(line.name());
            String points = points(line);
            int color = line.downed() ? Colors.RED : !line.alive() ? Colors.GREY : line.you() ? Colors.GOLD : Colors.WHITE;
            int nameColor = line.alive() ? Colors.GREY : Colors.DIM;
            boolean popups = line.you() && ClientConfig.SHOW_POPUPS.get();
            if (right) {
                int face = left + size.width() - FACE;
                int nameRight = face - FACE_GAP;
                int pointsRight = nameRight - names - COLUMN_GAP;
                drawFace(graphics, line, face, y);
                graphics.drawString(font, name, nameRight - font.width(name), y, nameColor);
                graphics.drawString(font, points, pointsRight - font.width(points), y, color);
                if (popups) {
                    drawPopups(graphics, font, pointsRight - font.width(points) - POPUP_GAP, y, true);
                }
            } else {
                int nameX = left + FACE + FACE_GAP;
                int pointsX = nameX + names + COLUMN_GAP;
                drawFace(graphics, line, left, y);
                graphics.drawString(font, name, nameX, y, nameColor);
                graphics.drawString(font, points, pointsX, y, color);
                if (popups) {
                    drawPopups(graphics, font, pointsX + font.width(points) + POPUP_GAP, y, false);
                }
            }
            y += font.lineHeight + ROW_GAP;
        }
    }

    /** "$1500": the dollar sign stands in for a points icon. */
    static String points(HudSyncPacket.Line line) {
        return "$" + line.points();
    }

    private static int names(Frame frame) {
        return frame.hud().players().stream().mapToInt(line -> frame.font().width(shortName(line.name()))).max().orElse(0);
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

    /** The +N / -N beside your points, level with them at first, rising as it fades: to the left of {@code x} when {@code leftOf}, else to its right. */
    private static void drawPopups(GuiGraphics graphics, Font font, int x, int y, boolean leftOf) {
        long now = Util.getMillis();
        for (HudState.Popup popup : HudState.popups()) {
            float age = (now - popup.at()) / (float) HudState.POPUP_MILLIS;
            int alpha = Math.max(8, (int) (255 * (1 - age)));
            String text = (popup.amount() > 0 ? "+" : "") + popup.amount();
            int color = popup.amount() > 0 ? Colors.GOLD : Colors.RED;
            graphics.drawString(font, text, leftOf ? x - font.width(text) : x, (int) (y - age * 18), (alpha << 24) | color);
        }
    }
}
