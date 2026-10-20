package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.net.HudSyncPacket;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The last game HUD the server sent this player. Stale after a couple of seconds, which means "not in a game". Also
 * keeps the recent "+N" point gains for the HUD to animate.
 */
public final class HudState {
    private static final long STALE_MILLIS = 2_000;
    static final long POPUP_MILLIS = 1_200;

    /** A change to this player's points, and when it arrived. */
    record Popup(int amount, long at) {}

    private static HudSyncPacket last = HudSyncPacket.NONE;
    private static long receivedAt = Long.MIN_VALUE / 2;
    private static final List<Popup> popups = new ArrayList<>();

    private HudState() {}

    public static void update(HudSyncPacket packet) {
        long now = Util.getMillis();
        if (inGame() && packet.points() != last.points()) {
            popups.add(new Popup(packet.points() - last.points(), now));
        }
        last = packet;
        receivedAt = now;
    }

    public static boolean inGame() {
        return Util.getMillis() - receivedAt < STALE_MILLIS;
    }

    public static HudSyncPacket hud() {
        return last;
    }

    public static int points() {
        return last.points();
    }

    /** A teammate the HUD says is down, by name, so the crosshair on them can offer a revive. */
    public static boolean downed(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player player && inGame()
                && hud().players().stream().anyMatch(line -> line.downed() && !line.you() && line.name().equals(player.getGameProfile().getName()));
    }

    /** Whether the HUD says this player is down. */
    public static boolean selfDowned() {
        return inGame() && hud().players().stream().anyMatch(line -> line.downed() && line.you());
    }

    public static boolean powerOn() {
        return last.powerOn();
    }

    /** Point changes younger than {@link #POPUP_MILLIS}, oldest first. */
    static List<Popup> popups() {
        long now = Util.getMillis();
        popups.removeIf(p -> now - p.at() > POPUP_MILLIS);
        return popups;
    }
}
