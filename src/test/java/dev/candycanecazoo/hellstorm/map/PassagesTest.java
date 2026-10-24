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

    /** Flat ground at y -1 and open sky above it. */
    private static CellProbe.Cell ground(BlockPos pos) {
        return pos.getY() < 0 ? CellProbe.Cell.SOLID : CellProbe.Cell.OPEN;
    }

    private static Passages.Result find(ZombiesMap map, CellProbe probe, int maxGap) {
        return Passages.find(map, ZoneIndex.of(map), probe, maxGap);
    }

    @Test
    void aOneCellDoorwayConnectsTheRooms() {
        CellProbe doorway = openings(rooms(1), Map.of(new BlockPos(3, 0, 1), CellProbe.Cell.OPEN, new BlockPos(3, 1, 1), CellProbe.Cell.OPEN));
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(1), doorway, 2).open());
        assertTrue(MapValidator.validate(rooms(1), doorway, 2).playable());
    }

    @Test
    void aHoleOneBlockHighIsTooLowToWalkThrough() {
        assertEquals(Set.of(), find(rooms(1), openings(rooms(1), Map.of(new BlockPos(3, 0, 1), CellProbe.Cell.OPEN)), 2).open());
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
        CellProbe probe = openings(rooms(1), Map.of(new BlockPos(3, 0, 0), CellProbe.Cell.CLOSED_DOOR, new BlockPos(3, 1, 0), CellProbe.Cell.CLOSED_DOOR,
                new BlockPos(3, 0, 2), CellProbe.Cell.OPEN, new BlockPos(3, 1, 2), CellProbe.Cell.OPEN));
        ValidationReport report = MapValidator.validate(rooms(1), probe, 2);
        assertFalse(report.has(MapValidator.UNREACHABLE_ZONE));
        assertFalse(report.has(MapValidator.CLOSED_DOOR_GAP));
    }

    @Test
    void aCorridorLongerThanTheGapLimitDoesNotConnect() {
        CellProbe corridor = openings(rooms(3), Map.of(
                new BlockPos(3, 0, 1), CellProbe.Cell.OPEN, new BlockPos(4, 0, 1), CellProbe.Cell.OPEN, new BlockPos(5, 0, 1), CellProbe.Cell.OPEN,
                new BlockPos(3, 1, 1), CellProbe.Cell.OPEN, new BlockPos(4, 1, 1), CellProbe.Cell.OPEN, new BlockPos(5, 1, 1), CellProbe.Cell.OPEN));
        assertEquals(Set.of(), find(rooms(3), corridor, 2).open(), "3 open cells must not bridge a 2-cell limit");
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(3), corridor, 3).open());
    }

    @Test
    void touchingZonesConnectWhereAMobCanStepAcross() {
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(0), PassagesTest::ground, 2).open());
    }

    @Test
    void doorBlocksDrawnInsideTouchingZonesKeepThemApartUntilTheyAreGone() {
        Set<BlockPos> door = Set.of(new BlockPos(2, 0, 1), new BlockPos(2, 1, 1));
        CellProbe walls = pos -> pos.getX() == 2 && !door.contains(pos) ? CellProbe.Cell.SOLID : ground(pos);
        CellProbe closed = pos -> door.contains(pos) ? CellProbe.Cell.SOLID : walls.at(pos);
        assertEquals(Set.of(), find(rooms(0), closed, 2).open(), "the door's blocks are the only way across");
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(0), walls, 2).open(), "bought: the blocks are air");
    }

    /**
     * A door players buy made of blocks a mob walks through, such as a wooden door, which the world reads as open. It is
     * still shut until bought, so the zones stay apart; once bought its blocks are air and the doorway connects them.
     */
    @Test
    void aBoughtDoorOfPassableBlocksKeepsTheZonesApartUntilItIsOpen() {
        ZombiesMap map = rooms(1).withDoor(DoorDef.empty("oak", 750).withMember(new BlockPos(3, 0, 1)).withMember(new BlockPos(3, 1, 1)));
        CellProbe world = openings(map, Map.of(new BlockPos(3, 0, 1), CellProbe.Cell.OPEN, new BlockPos(3, 1, 1), CellProbe.Cell.OPEN));
        assertEquals(Set.of(), find(map, world, 2).open(), "a closed door is no passage, whatever it's made of");
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), Passages.find(map, ZoneIndex.of(map), world, 2, Set.of("oak")).open());
        assertFalse(MapValidator.validate(map, world, 2).has(MapValidator.LOOPHOLE), "the door is the only way through");
    }

    /**
     * The Test map's barrel door: two blocks thick inside the east zone's box (x 3 and 4), with an air gap in the near layer.
     * The gap touches the west zone but is sealed off from the rest of the east zone, so it mustn't connect them.
     */
    @Test
    void aGapInsideAThickDoorDoesNotConnectTheZones() {
        BlockPos gap = new BlockPos(3, 1, 1);
        Set<BlockPos> doorway = Set.of(new BlockPos(3, 0, 1), gap, new BlockPos(4, 0, 1), new BlockPos(4, 1, 1));
        CellProbe closed = pos -> (pos.getX() == 3 && !pos.equals(gap)) || pos.getX() == 4 ? CellProbe.Cell.SOLID : ground(pos);
        assertEquals(Set.of(), find(rooms(0), closed, 2).open(), "the gap is a pocket of the east zone, not a way in");
        CellProbe bought = pos -> (pos.getX() == 3 || pos.getX() == 4) && !doorway.contains(pos) ? CellProbe.Cell.SOLID : ground(pos);
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(rooms(0), bought, 2).open(), "bought, the doorway runs through");
    }

    /**
     * Two fields under open sky, drawn ten blocks tall and touching at x 4|5, split by a wall along x 4 inside the west
     * field. The wall is {@code height} blocks of {@code wall}, with a door players buy, "gate", two blocks tall at z 2.
     */
    private static ZombiesMap fields() {
        return ZombiesMap.EMPTY
                .withZone(new Zone(WEST, List.of(new BlockBox(0, 0, 0, 4, 9, 4)), true))
                .withZone(new Zone(EAST, List.of(new BlockBox(5, 0, 0, 9, 9, 4)), false))
                .withFeature(new BlockPos(1, 0, 1), FeatureKind.SPAWN)
                .withFeature(new BlockPos(8, 0, 1), FeatureKind.SPAWN)
                .withDoor(DoorDef.empty("gate", 750).withMember(new BlockPos(4, 0, 2)).withMember(new BlockPos(4, 1, 2)));
    }

    private static CellProbe fieldWall(int height, CellProbe.Cell wall) {
        return pos -> pos.getX() == 4 && pos.getY() >= 0 && pos.getY() < height ? wall : ground(pos);
    }

    @Test
    void aWallTwoBlocksHighKeepsFieldsApartThoughTheAirAboveItTouches() {
        CellProbe world = fieldWall(2, CellProbe.Cell.SOLID);
        assertEquals(Set.of(), find(fields(), world, 2).open(), "nothing climbs a two-block wall");
        ValidationReport report = MapValidator.validate(fields(), world, 2);
        assertFalse(report.has(MapValidator.LOOPHOLE), report.problems().toString());
        assertFalse(report.has(MapValidator.UNREACHABLE_ZONE), "the gate joins them: " + report.problems());
        CellProbe bought = pos -> pos.getX() == 4 && pos.getZ() == 2 ? ground(pos) : world.at(pos);
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), Passages.find(fields(), ZoneIndex.of(fields()), bought, 2, Set.of("gate")).open());
    }

    @Test
    void aWallOneBlockHighIsJumpedSoTheGateIsALoophole() {
        CellProbe world = fieldWall(1, CellProbe.Cell.SOLID);
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(fields(), world, 2).open());
        assertTrue(MapValidator.validate(fields(), world, 2).has(MapValidator.LOOPHOLE));
    }

    @Test
    void aFenceIsNotJumpedEvenOneBlockHigh() {
        assertEquals(Set.of(), find(fields(), fieldWall(1, CellProbe.Cell.TALL), 2).open());
    }

    @Test
    void aDropFromHigherGroundJoinsTheZonesBelow() {
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(WEST, List.of(new BlockBox(0, 4, 0, 4, 9, 4)), true))
                .withZone(new Zone(EAST, List.of(new BlockBox(5, 0, 0, 9, 9, 4)), false));
        CellProbe cliff = pos -> pos.getX() <= 4 && pos.getY() < 4 ? CellProbe.Cell.SOLID : ground(pos);
        assertEquals(Set.of(Passages.Link.of(WEST, EAST)), find(map, cliff, 2).open(), "a player can jump down past a door");
    }
}
