package dev.candycanecazoo.packapunch.perk;

import dev.candycanecazoo.packapunch.client.ClientPerks;
import dev.candycanecazoo.packapunch.net.PerkSyncPacket;
import dev.candycanecazoo.packapunch.tacz.ReloadTimes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The client plays the local player's reload animations at the synced Speed Cola pace, and nothing else faster. */
class ReloadAnimationTest {
    @AfterEach
    void reset() {
        ClientPerks.update(PerkSyncPacket.NONE);
    }

    @Test
    void reloadAnimationsRunAtThePerkPace() {
        ClientPerks.update(new PerkSyncPacket(List.of(), 1f, 0.5f));
        assertEquals(2_000L, ReloadTimes.animationStep("reload_empty", 1_000L));
        assertEquals(2_000L, ReloadTimes.animationStep("reload_loop", 1_000L));
        assertEquals(1_000L, ReloadTimes.animationStep("draw", 1_000L));
        assertEquals(1_000L, ReloadTimes.animationStep("inspect", 1_000L));
    }

    @Test
    void withoutThePerkNothingChanges() {
        assertEquals(1_000L, ReloadTimes.animationStep("reload_tactical", 1_000L));
    }
}
