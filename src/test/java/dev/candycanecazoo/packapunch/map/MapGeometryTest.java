package dev.candycanecazoo.packapunch.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapGeometryTest {
    private static final ZoneId HALL = new ZoneId("hall");

    private static ZombiesMap sample() {
        return ZombiesMap.EMPTY
                .withZone(new Zone(HALL, List.of(new BlockBox(10, 64, 10, 19, 67, 15), new BlockBox(20, 64, 12, 24, 66, 13)), true))
                .withFeature(new BlockPos(-3, 70, 11), FeatureKind.SPAWN)
                .withWindow(new WindowDef("w", new BlockBox(25, 65, 12, 25, 66, 13), Direction.EAST, 6, HALL))
                .withDoor(DoorDef.empty("gate", 750).withMember(new BlockPos(14, 63, 30)));
    }

    @Test
    void boundsCoverEveryZoneFeatureWindowAndDoorBlock() {
        assertEquals(Optional.of(new BlockBox(-3, 63, 10, 25, 70, 30)), MapGeometry.bounds(sample()));
        assertEquals(Optional.empty(), MapGeometry.bounds(ZombiesMap.EMPTY));
    }

    @Test
    void movingThereAndBackIsTheSameMap() {
        ZombiesMap map = sample();
        ZombiesMap moved = MapGeometry.moved(map, 1000, -20, -7);
        assertEquals(Optional.of(new BlockBox(997, 43, 3, 1025, 50, 23)), MapGeometry.bounds(moved));
        assertEquals(new BlockPos(997, 50, 4), moved.features().keySet().iterator().next());
        assertEquals(map, MapGeometry.moved(moved, -1000, 20, 7));
    }
}
