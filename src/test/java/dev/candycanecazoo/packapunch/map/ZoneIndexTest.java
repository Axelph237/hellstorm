package dev.candycanecazoo.packapunch.map;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneIndexTest {
    private static final ZoneId HALL = new ZoneId("hall");
    private static final ZoneId ROOM = new ZoneId("room");

    private final ZoneIndex index = new ZoneIndex(List.of(
            new Zone(HALL, List.of(new BlockBox(-20, 60, -3, 40, 64, 3), new BlockBox(10, 60, 4, 14, 64, 30)), true),
            new Zone(ROOM, List.of(new BlockBox(41, 60, -3, 50, 70, 3)), false)));

    @Test
    void findsZoneInEveryBoxOfAMultiBoxZoneAcrossSections() {
        assertEquals(Optional.of(HALL), index.zoneAt(new BlockPos(-20, 60, -3)));
        assertEquals(Optional.of(HALL), index.zoneAt(new BlockPos(40, 64, 3)));
        assertEquals(Optional.of(HALL), index.zoneAt(new BlockPos(12, 62, 29)));
        assertEquals(Optional.of(ROOM), index.zoneAt(new BlockPos(41, 70, 0)));
    }

    @Test
    void positionsJustOutsideAreInNoZone() {
        assertEquals(Optional.empty(), index.zoneAt(new BlockPos(-21, 60, 0)));
        assertEquals(Optional.empty(), index.zoneAt(new BlockPos(0, 65, 0)));
        assertEquals(Optional.empty(), index.zoneAt(new BlockPos(15, 62, 20)));
        assertEquals(Optional.empty(), index.zoneAt(new BlockPos(51, 60, 0)));
    }

    @Test
    void agreesWithLinearScanOnEveryCellNearTheZones() {
        ZombiesMap map = new ZombiesMap(java.util.Map.of(), java.util.Map.of())
                .withZone(new Zone(HALL, List.of(new BlockBox(-20, 60, -3, 40, 64, 3), new BlockBox(10, 60, 4, 14, 64, 30)), true))
                .withZone(new Zone(ROOM, List.of(new BlockBox(41, 60, -3, 50, 70, 3)), false));
        ZoneIndex built = ZoneIndex.of(map);
        for (int x = -24; x <= 54; x++) {
            for (int y = 58; y <= 72; y += 2) {
                for (int z = -6; z <= 33; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    assertEquals(map.zoneAt(pos).map(Zone::id), built.zoneAt(pos), pos::toShortString);
                }
            }
        }
    }
}
