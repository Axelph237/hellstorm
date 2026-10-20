package dev.candycanecazoo.packapunch.map;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZombiesMapCodecTest {
    private static final ZombiesMap MAP = ZombiesMap.EMPTY
            .withZone(new Zone(new ZoneId("start"), List.of(new BlockBox(0, 60, 0, 9, 63, 9), new BlockBox(10, 60, 0, 12, 62, 3)), true))
            .withZone(new Zone(new ZoneId("hall"), List.of(new BlockBox(-5, 60, -5, -1, 64, 20)), false))
            .withFeature(new BlockPos(3, 60, 3), FeatureKind.SPAWN)
            .withDoor(DoorDef.empty("gate", 1000).withMember(new BlockPos(13, 60, 1)).withMember(new BlockPos(13, 61, 1))
                    .withPower(true).withStyle(DoorDef.Style.DEBRIS));

    @Test
    void roundTripsThroughNbt() {
        Tag tag = ZombiesMap.CODEC.encodeStart(NbtOps.INSTANCE, MAP).getOrThrow(false, AssertionError::new);
        assertEquals(MAP, ZombiesMap.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(false, AssertionError::new));
    }

    @Test
    void roundTripsThroughJson() {
        JsonElement json = ZombiesMap.CODEC.encodeStart(JsonOps.INSTANCE, MAP).getOrThrow(false, AssertionError::new);
        assertEquals(MAP, ZombiesMap.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, AssertionError::new));
    }

    @Test
    void rejectsInvalidZoneIds() {
        assertThrows(IllegalArgumentException.class, () -> new ZoneId("Has Spaces"));
        assertTrue(ZoneId.parse("UPPER").error().isPresent());
    }

    @Test
    void doorsDefaultWhatTheyLeaveOutAndRejectBadStyles() {
        DoorDef door = DoorDef.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"id\": \"gate\"}"))
                .getOrThrow(false, AssertionError::new);
        assertEquals(DoorDef.empty("gate", 750), door);
        assertTrue(DoorDef.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"id\": \"gate\", \"style\": \"portal\"}"))
                .error().isPresent());
    }
}
