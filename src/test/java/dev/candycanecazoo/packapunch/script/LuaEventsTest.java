package dev.candycanecazoo.packapunch.script;

import dev.candycanecazoo.packapunch.game.GameEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaEventsTest {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final ResourceLocation MAX_AMMO = ResourceLocation.tryBuild("packapunch", "max_ammo");

    @Test
    void everyEventTypeHasAScriptName() {
        assertEquals(LuaEvents.NAMES.size(), GameEvent.class.getPermittedSubclasses().length,
                "each GameEvent record needs a name in LuaEvents.NAMES, and each name an event");
    }

    @Test
    void survivalEventsArriveWithTheirFields() {
        List<GameEvent> events = List.of(
                new GameEvent.PlayerDowned(ALICE, new BlockPos(1, 2, 3)),
                new GameEvent.PlayerRevived(ALICE, Optional.of(BOB)),
                new GameEvent.PlayerBledOut(ALICE),
                new GameEvent.PowerUpSpawned(MAX_AMMO, new BlockPos(4, 5, 6)),
                new GameEvent.PowerUpCollected(MAX_AMMO, BOB),
                new GameEvent.TrapActivated(BOB, new BlockPos(7, 8, 9)),
                new GameEvent.WaveEventStarted(ResourceLocation.tryBuild("packapunch", "hounds"), 5));
        for (GameEvent event : events) {
            assertTrue(LuaEvents.NAMES.contains(LuaEvents.name(event)), LuaEvents.name(event));
        }
        LuaTable downed = LuaEvents.table(events.get(0));
        assertEquals("player_downed", downed.get("event").tojstring());
        assertEquals(ALICE.toString(), downed.get("player").tojstring());
        assertEquals(3, downed.get("z").toint());
        assertEquals(BOB.toString(), LuaEvents.table(events.get(1)).get("by").tojstring());
        assertTrue(LuaEvents.table(new GameEvent.PlayerRevived(ALICE, Optional.empty())).get("by").isnil(), "a self-revive has no reviver");
        assertEquals("packapunch:max_ammo", LuaEvents.table(events.get(4)).get("powerup").tojstring());
        LuaTable wave = LuaEvents.table(events.get(6));
        assertEquals("packapunch:hounds", wave.get("wave_event").tojstring());
        assertEquals(5, LuaEvents.round(events.get(6)).getAsInt());
    }
}
