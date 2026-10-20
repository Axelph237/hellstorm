package dev.candycanecazoo.packapunch.pap;

import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.script.LuaEvents;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PapUsedEventTest {
    @Test
    void scriptsSeePapUsedWithThePlayerAndItem() {
        UUID player = UUID.randomUUID();
        GameEvent event = new GameEvent.PapUsed(player, "tacz:ak47");
        assertEquals("pap_used", LuaEvents.name(event));
        assertTrue(LuaEvents.NAMES.contains("pap_used"));
        LuaTable table = LuaEvents.table(event);
        assertEquals("pap_used", table.get("event").tojstring());
        assertEquals(player.toString(), table.get("player").tojstring());
        assertEquals("tacz:ak47", table.get("item").tojstring());
    }
}
