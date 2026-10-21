package dev.candycanecazoo.hellstorm.map;

import dev.candycanecazoo.hellstorm.map.validate.MapValidator;
import dev.candycanecazoo.hellstorm.map.validate.ValidationReport;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two 3x3x3 rooms on the x axis: "west" at x 0..2, "east" starting after a gap. The rooms are air, and everything outside
 * them is solid except the cells the probe in each test opens up.
 */
class PassagesTest {
    private static final ZoneId WEST = new ZoneId("west");
    private static final ZoneId EAST = new ZoneId("east");

    private static ZombiesMap rooms(int gap) {
        int eastMin = 3 + gap;
        return ZombiesMap.EMPTY
                .withZone(new Zone(WEST, List.of(new BlockBox(0, 0, 0, 2, 2, 2)), true))
                .withZone(new Zone(EAST, List.of(new BlockBox(eastMin, 0, 0, eastMin + 2, 2, 2)), false))
                .withFeature(new BlockPos(1, 0, 1), FeatureKind.SPAWN)
                .withFeature(new BlockPos(eastMin + 1, 0, 1), FeatureKind.SPAWN);
    }

    /** The rooms are air inside, the listed cells are as given, and everything else is solid. */
    private static CellProbe openings(ZombiesMap map, Map<BlockPos, CellProbe.Cell> cells) {
        ZoneIndex index = ZoneIndex.of(map);
        return pos -> cells.getOrDefault(pos, index.zoneAt(pos).isPresent() ? CellProbe.Cell.OPEN : CellProbe.Cell.SOLID);
    }

    private static Passages.Result find(ZombiesMap map, CellProbe probe, int maxGap) {
        return Passages.find(map, ZoneIndex.of(map), probe, maxGap);
    }

    @Test
    void aOneCellDoorwayConnectsTheRooms() {
        Passages.Result result = find(rooms(1), openings(rooms(1), Map.of(new BlockPos(3, 0, 1), CellProbe.Cell.OPEN, new BlockPos(3, 1, 1), CellProbe.Cell.OPEN)), 2);
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), result.open());
        assertTrue(MapValidator.validate(rooms(1), openings(rooms(1), Map.of(new BlockPos(3, 0, 1), CellProbe.Cell.OPEN)), 2).playable());
    }

    @Test
    void aSolidWallDoesNotConnect() {
        assertEquals(Set.of(), find(rooms(1), CellProbe.ALL_SOLID, 2).open());
        ValidationReport report = MapValidator.validate(rooms(1), CellProbe.ALL_SOLID, 2);
        assertTrue(report.has(MapValidator.UNREACHABLE_ZONE));
        assertFalse(report.has(MapValidator.CLOSED_DOOR_GAP));
    }

    @Test
    void aClosedDoorIsNotAPassageButIsReportedWhereItIs() {
        BlockPos door = new BlockPos(3, 0, 1);
        CellProbe probe = openings(rooms(1), Map.of(door, CellProbe.Cell.CLOSED_DOOR, door.above(), CellProbe.Cell.CLOSED_DOOR));
        Passages.Result result = find(rooms(1), probe, 2);
        assertEquals(Set.of(), result.open());
        assertTrue(result.closedDoors().containsKey(Passages.Link.of(WEST, EAST)));
        ValidationReport report = MapValidator.validate(rooms(1), probe, 2);
        assertTrue(report.has(MapValidator.UNREACHABLE_ZONE));
        BlockPos reported = report.problems().stream().filter(p -> p.code().equals(MapValidator.CLOSED_DOOR_GAP))
                .findFirst().orElseThrow().pos().orElseThrow();
        assertTrue(reported.equals(door) || reported.equals(door.above()), "door reported at " + reported);
    }

    @Test
    void anOpenDoorwayNextToAClosedDoorStillConnectsWithoutAWarning() {
        CellProbe probe = openings(rooms(1), Map.of(new BlockPos(3, 0, 0), CellProbe.Cell.CLOSED_DOOR, new BlockPos(3, 0, 2), CellProbe.Cell.OPEN));
        ValidationReport report = MapValidator.validate(rooms(1), probe, 2);
        assertFalse(report.has(MapValidator.UNREACHABLE_ZONE));
        assertFalse(report.has(MapValidator.CLOSED_DOOR_GAP));
    }

    @Test
    void aCorridorLongerThanTheGapLimitDoesNotConnect() {
        CellProbe corridor = openings(rooms(3), Map.of(
                new BlockPos(3, 0, 1), CellProbe.Cell.OPEN, new BlockPos(4, 0, 1), CellProbe.Cell.OPEN, new BlockPos(5, 0, 1), CellProbe.Cell.OPEN));
        assertEquals(Set.of(), find(rooms(3), corridor, 2).open(), "3 open cells must not bridge a 2-cell limit");
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(3), corridor, 3).open());
    }

    @Test
    void touchingZonesConnectWhereAMobCanStepAcross() {
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(0), pos -> CellProbe.Cell.OPEN, 2).open());
    }

    @Test
    void doorBlocksDrawnInsideTouchingZonesKeepThemApartUntilTheyAreGone() {
        Set<BlockPos> door = Set.of(new BlockPos(2, 0, 1), new BlockPos(2, 1, 1));
        CellProbe walls = pos -> pos.getX() == 2 && !door.contains(pos) ? CellProbe.Cell.SOLID : CellProbe.Cell.OPEN;
        CellProbe closed = pos -> door.contains(pos) ? CellProbe.Cell.SOLID : walls.at(pos);
        assertEquals(Set.of(), find(rooms(0), closed, 2).open(), "the door's blocks are the only way across");
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(0), walls, 2).open(), "bought: the blocks are air");
    }

    /**
     * The Test map's barrel door: two blocks thick inside the east zone's box (x 3 and 4), with an air gap in the near layer.
     * The gap touches the west zone but is sealed off from the rest of the east zone, so it mustn't connect them.
     */
    @Test
    void aGapInsideAThickDoorDoesNotConnectTheZones() {
        BlockPos gap = new BlockPos(3, 1, 1);
        Set<BlockPos> doorway = Set.of(new BlockPos(3, 0, 1), gap, new BlockPos(4, 0, 1), new BlockPos(4, 1, 1));
        CellProbe closed = pos -> (pos.getX() == 3 && !pos.equals(gap)) || pos.getX() == 4 ? CellProbe.Cell.SOLID : CellProbe.Cell.OPEN;
        assertEquals(Set.of(), find(rooms(0), closed, 2).open(), "the gap is a pocket of the east zone, not a way in");
        CellProbe bought = pos -> (pos.getX() == 3 || pos.getX() == 4) && !doorway.contains(pos) ? CellProbe.Cell.SOLID : CellProbe.Cell.OPEN;
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(0), bought, 2).open(), "bought, the doorway runs through");
    }
}
