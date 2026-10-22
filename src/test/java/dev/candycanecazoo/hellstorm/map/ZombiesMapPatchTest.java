package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZombiesMapPatchTest {
    private static final Zone HALL = new Zone(new ZoneId("hall"), List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true);
    private static final Zone SIDE = new Zone(new ZoneId("side"), List.of(new BlockBox(10, 0, 0, 14, 3, 9)), false);
    private static final DoorDef GATE = DoorDef.empty("gate", 750).withMember(new BlockPos(10, 0, 4));

    @Test
    void undoingAMergeBringsBackBothRoomsAndKeepsEditsMadeSince() {
        ZombiesMap before = ZombiesMap.EMPTY.withZone(HALL).withZone(SIDE);
        ZombiesMap after = before.withoutZone(SIDE.id()).withZone(HALL.withBoxes(SIDE.boxes()));
        BlockPos marker = new BlockPos(3, 0, 3);
        ZombiesMap now = after.withDoor(GATE).withFeature(marker, FeatureKind.SPAWN);

        ZombiesMap undone = now.patched(after, before);

        assertEquals(HALL, undone.zone(HALL.id()).orElseThrow());
        assertEquals(SIDE, undone.zone(SIDE.id()).orElseThrow());
        assertEquals(GATE, undone.doors().get("gate"), "a door added after the merge stays");
        assertEquals(FeatureKind.SPAWN, undone.features().get(marker), "a feature added after the merge stays");
        assertEquals(now, undone.patched(before, after), "redo replays the merge onto what's there now");
    }

    @Test
    void undoingTheAddThatMadeADoorRemovesIt() {
        ZombiesMap before = ZombiesMap.EMPTY.withZone(HALL);
        ZombiesMap after = before.withDoor(GATE);
        assertEquals(before, after.patched(after, before));
        assertEquals(after, before.patched(before, after));
    }
}
