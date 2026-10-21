package dev.candycanecazoo.hellstorm.perk;

import dev.candycanecazoo.hellstorm.tacz.ReloadTimes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The client plays reload animations at the local player's reload speed, and nothing else faster. */
class ReloadAnimationTest {
    @Test
    void reloadAnimationsRunAtTheReloadSpeed() {
        assertEquals(2_000L, ReloadTimes.animationStep("reload_empty", 1_000L, 2.0));
        assertEquals(2_000L, ReloadTimes.animationStep("reload_loop", 1_000L, 2.0));
        assertEquals(1_000L, ReloadTimes.animationStep("draw", 1_000L, 2.0));
        assertEquals(1_000L, ReloadTimes.animationStep("inspect", 1_000L, 2.0));
    }

    @Test
    void atNormalSpeedNothingChanges() {
        assertEquals(1_000L, ReloadTimes.animationStep("reload_tactical", 1_000L, 1.0));
    }
}
