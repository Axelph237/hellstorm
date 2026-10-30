package dev.candycanecazoo.hellstorm.client.anim;

import net.minecraft.Util;

/** The clock UI animations run on: the client's own milliseconds, which, unlike the game time, never jump when the server lags. */
public final class UiClock {
    private UiClock() {}

    public static long now() {
        return Util.getMillis();
    }
}
