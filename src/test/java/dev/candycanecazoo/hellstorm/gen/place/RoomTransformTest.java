package dev.candycanecazoo.hellstorm.gen.place;

import dev.candycanecazoo.hellstorm.gen.layout.Box;
import dev.candycanecazoo.hellstorm.gen.layout.Rotation;
import dev.candycanecazoo.hellstorm.gen.layout.Size;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoomTransformTest {
    private static final Size SIZE = new Size(9, 5, 15);
    private static final BlockPos CORNER = new BlockPos(100, 64, -40);

    /** What Minecraft's StructureTemplate does to a block at x, z on each turn, about 0, 0, 0. */
    private static int[] minecraft(Rotation r, int x, int z) {
        return switch (r) {
            case NONE -> new int[] {x, z};
            case CLOCKWISE_90 -> new int[] {-z, x};
            case CLOCKWISE_180 -> new int[] {-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[] {z, -x};
        };
    }

    @Test
    void placingTheTemplateAtItsPositionPutsEveryBlockWhereTheLayoutSays() {
        for (Rotation r : Rotation.values()) {
            RoomTransform t = new RoomTransform(r, SIZE, CORNER);
            BlockPos at = t.templatePosition();
            for (int x = 0; x < SIZE.x(); x++) {
                for (int z = 0; z < SIZE.z(); z++) {
                    int[] mc = minecraft(r, x, z);
                    assertEquals(t.apply(x, 3, z), at.offset(mc[0], 3, mc[1]), r + " at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void aTurnedRoomStaysInTheBoxTheLayoutGivesIt() {
        for (Rotation r : Rotation.values()) {
            RoomTransform t = new RoomTransform(r, SIZE, CORNER);
            BlockBox room = t.apply(SIZE.box());
            Size turned = r.apply(SIZE);
            assertEquals(BlockBox.of(CORNER, CORNER.offset(turned.x() - 1, turned.y() - 1, turned.z() - 1)), room, r.toString());
        }
    }

    @Test
    void aSliceTurnsWithItsRoomAndItsIdsTakeThePrefix() {
        ZombiesMap slice = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("hall"), List.of(new BlockBox(1, 1, 1, 7, 4, 13)), true))
                .withFeature(new BlockPos(1, 1, 1), FeatureKind.SPAWN)
                .withWindow(new WindowDef("w", new BlockBox(3, 1, 0, 5, 2, 0), Direction.NORTH, 6));
        RoomTransform t = new RoomTransform(Rotation.CLOCKWISE_90, SIZE, CORNER);
        ZombiesMap placed = t.apply(slice, "r3", false);
        Zone zone = placed.zones().get(new ZoneId("r3_hall"));
        assertNotNull(zone);
        assertFalse(zone.start(), "only the start room's zones start");
        assertEquals(List.of(t.apply(new BlockBox(1, 1, 1, 7, 4, 13))), zone.boxes());
        assertEquals(Map.of(t.apply(new BlockPos(1, 1, 1)), FeatureKind.SPAWN), placed.features());
        WindowDef window = placed.windows().get("r3_w");
        assertEquals(Direction.EAST, window.outward(), "a north window faces east once turned clockwise");
        assertEquals(t.apply(new Box(3, 1, 0, 5, 2, 0)), window.opening());
    }

    @Test
    void longZoneIdsAreCutButStayDistinct() {
        ZoneId a = RoomTransform.zoneId("r12", new ZoneId("a_zone_name_of_thirty_chars_aa"));
        ZoneId b = RoomTransform.zoneId("r12", new ZoneId("a_zone_name_of_thirty_chars_ab"));
        assertTrue(a.value().length() <= 32 && b.value().length() <= 32);
        assertNotEquals(a, b);
    }
}
