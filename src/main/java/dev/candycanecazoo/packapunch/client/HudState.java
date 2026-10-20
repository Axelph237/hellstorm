package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.net.HudSyncPacket;
import net.minecraft.Util;

/** The last game status the server sent this player. Stale after a couple of seconds, which means "not in a game". */
public final class HudState {
    private static final long STALE_MILLIS = 2_000;

    private static HudSyncPacket last = new HudSyncPacket(0, false);
    private static long receivedAt = Long.MIN_VALUE / 2;

    private HudState() {}

    public static void update(HudSyncPacket packet) {
        last = packet;
        receivedAt = Util.getMillis();
    }

    public static boolean inGame() {
        return Util.getMillis() - receivedAt < STALE_MILLIS;
    }

    public static int points() {
        return last.points();
    }

    public static boolean powerOn() {
        return last.powerOn();
    }
}
