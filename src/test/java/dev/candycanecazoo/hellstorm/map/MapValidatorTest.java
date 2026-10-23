package dev.candycanecazoo.hellstorm.map;

import dev.candycanecazoo.hellstorm.map.validate.MapValidator;
import dev.candycanecazoo.hellstorm.map.validate.Problem;
import dev.candycanecazoo.hellstorm.map.validate.ValidationReport;
import dev.candycanecazoo.hellstorm.util.Highlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapValidatorTest {
    private static final ZoneId START = new ZoneId("start");
    private static final ZoneId NEXT = new ZoneId("next");

    private static ZombiesMap twoTouchingZonesWithSpawns() {
        return ZombiesMap.EMPTY
                .withZone(new Zone(START, List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true))
                .withZone(new Zone(NEXT, List.of(new BlockBox(10, 0, 0, 19, 3, 9)), false))
                .withFeature(new BlockPos(2, 0, 2), FeatureKind.SPAWN)
                .withFeature(new BlockPos(12, 0, 2), FeatureKind.SPAWN);
    }

    @Test
    void aValidMapHasNoProblems() {
        ValidationReport report = MapValidator.validate(twoTouchingZonesWithSpawns());
        assertEquals(List.of(), report.problems());
        assertTrue(report.playable());
    }

    @Test
    void emptyMapIsNotPlayable() {
        ValidationReport report = MapValidator.validate(ZombiesMap.EMPTY);
        assertTrue(report.has(MapValidator.NO_ZONES));
        assertFalse(report.playable());
    }

    @Test
    void missingStartZoneIsAnError() {
        ZombiesMap map = twoTouchingZonesWithSpawns().withZone(new Zone(START, List.of(new BlockBox(0, 0, 0, 9, 3, 9)), false));
        ValidationReport report = MapValidator.validate(map);
        assertTrue(report.has(MapValidator.NO_START_ZONE));
        assertFalse(report.playable());
    }

    @Test
    void zoneWithNoPassageIsUnreachable() {
        ZombiesMap map = twoTouchingZonesWithSpawns().withZone(new Zone(NEXT, List.of(new BlockBox(11, 0, 0, 19, 3, 9)), false));
        ValidationReport report = MapValidator.validate(map);
        assertTrue(report.has(MapValidator.UNREACHABLE_ZONE));
        assertTrue(report.problems().stream().filter(p -> p.code().equals(MapValidator.UNREACHABLE_ZONE)).allMatch(p -> p.pos().isEmpty()),
                "zone-level problems must not point at an arbitrary block");
    }

    @Test
    void reachabilityFollowsChainsOfPassages() {
        ZombiesMap map = twoTouchingZonesWithSpawns()
                .withZone(new Zone(new ZoneId("far"), List.of(new BlockBox(20, 0, 0, 29, 3, 9)), false))
                .withFeature(new BlockPos(22, 0, 2), FeatureKind.SPAWN);
        assertFalse(MapValidator.validate(map).has(MapValidator.UNREACHABLE_ZONE));
    }

    @Test
    void overlappingZonesAreAnErrorAtTheOverlap() {
        ZombiesMap map = twoTouchingZonesWithSpawns().withZone(new Zone(NEXT, List.of(new BlockBox(8, 0, 0, 19, 3, 9)), false));
        ValidationReport report = MapValidator.validate(map);
        assertEquals(new BlockPos(8, 0, 0), report.problems().stream()
                .filter(p -> p.code().equals(MapValidator.ZONE_OVERLAP)).findFirst().orElseThrow().pos().orElseThrow());
    }

    @Test
    void spawnsAreWarningsNotErrors() {
        ZombiesMap map = twoTouchingZonesWithSpawns()
                .withoutFeature(new BlockPos(12, 0, 2))
                .withFeature(new BlockPos(50, 0, 50), FeatureKind.SPAWN);
        ValidationReport report = MapValidator.validate(map);
        assertTrue(report.has(MapValidator.ZONE_WITHOUT_SPAWN));
        assertTrue(report.problems().stream().filter(p -> p.code().equals(MapValidator.ZONE_WITHOUT_SPAWN)).allMatch(p -> p.pos().isEmpty()),
                "zone-level problems must not point at an arbitrary block");
        assertTrue(report.has(MapValidator.SPAWN_OUTSIDE_ZONE));
        assertEquals(new BlockPos(50, 0, 50), report.problems().stream()
                .filter(p -> p.code().equals(MapValidator.SPAWN_OUTSIDE_ZONE)).findFirst().orElseThrow().pos().orElseThrow());
        assertTrue(report.playable());
    }

    @Test
    void zoneWithNoBoxesIsAnError() {
        ValidationReport report = MapValidator.validate(twoTouchingZonesWithSpawns().withZone(Zone.empty(new ZoneId("ghost"))));
        assertTrue(report.has(MapValidator.EMPTY_ZONE));
    }

    @Test
    void windowChecks() {
        ZombiesMap map = twoTouchingZonesWithSpawns()
                .withWindow(new WindowDef("huge", new BlockBox(2, 0, -1, 9, 1, -1), Direction.NORTH, 6, START))
                .withWindow(new WindowDef("lost", new BlockBox(12, 0, -1, 12, 1, -1), Direction.NORTH, 6, new ZoneId("nowhere")))
                .withFeature(new BlockPos(12, 0, -3), FeatureKind.SPAWN)
                .withWindow(new WindowDef("orphan", new BlockBox(25, 0, -1, 25, 1, -1), Direction.NORTH, 6, NEXT));
        ValidationReport report = MapValidator.validate(map, new dev.candycanecazoo.hellstorm.map.validate.ValidationContext(
                CellProbe.zonesOnly(ZoneIndex.of(map)), pos -> false, 2, new dev.candycanecazoo.hellstorm.config.GameSettings.Windows(5, 4, 30, 15, 6.0),
                List.of(new dev.candycanecazoo.hellstorm.map.validate.ValidationContext.MobSize("test:giant", 3.6f, 12f))));
        assertTrue(report.has(MapValidator.WINDOW_TOO_LARGE), "8 wide is over 5");
        assertEquals(List.of("orphan"), report.problems().stream().filter(p -> p.code().equals(MapValidator.WINDOW_UNKNOWN_ZONE))
                .map(p -> Highlight.plain(p.message()).split(" ")[1]).toList(), "only a window with no zone right behind it opens into no zone");
        assertTrue(report.problems().stream().anyMatch(p -> p.code().equals(MapValidator.WINDOW_WITHOUT_SPAWN) && p.message().contains(Highlight.key("huge"))));
        assertFalse(report.problems().stream().anyMatch(p -> p.code().equals(MapValidator.WINDOW_WITHOUT_SPAWN) && p.message().contains(Highlight.key("lost"))),
                "the marker in front of 'lost' links to it and feeds the zone behind it, whatever zone the window was made in");
        assertTrue(report.has(MapValidator.MOB_TOO_BIG_FOR_WINDOW));
    }

    /** A start zone drawn as one room plus a stray box past its wall: floor everywhere at y = 0, a wall at x = 10. */
    private static ZombiesMap startWithStrayBox() {
        return twoTouchingZonesWithSpawns().withZone(new Zone(START,
                List.of(new BlockBox(0, 0, 0, 9, 3, 9), new BlockBox(11, 0, -6, 15, 0, -2)), true));
    }

    private static boolean standable(BlockPos pos) {
        return pos.getY() == 0 && pos.getX() != 10;
    }

    @Test
    void aStartZoneSplitByAWallKeepsItsLargestPieceAndWarnsAboutTheRest() {
        Zone start = startWithStrayBox().zones().get(START);
        List<List<BlockPos>> pieces = StandingArea.pieces(start, MapValidatorTest::standable);
        assertEquals(List.of(100, 25), pieces.stream().map(List::size).toList(), "the room first, then the stray box");

        ValidationReport report = MapValidator.validate(startWithStrayBox(), new dev.candycanecazoo.hellstorm.map.validate.ValidationContext(
                CellProbe.zonesOnly(ZoneIndex.of(startWithStrayBox())), MapValidatorTest::standable, 2, dev.candycanecazoo.hellstorm.config.GameSettings.DEFAULT.windows(), List.of()));
        List<Problem> split = report.problems().stream().filter(p -> p.code().equals(MapValidator.SPLIT_START_ZONE)).toList();
        assertEquals(1, split.size());
        assertTrue(new BlockBox(11, 0, -6, 15, 0, -2).contains(split.get(0).pos().orElseThrow()), "points into the stray box");
        assertTrue(report.playable(), "a warning, not an error");
    }

    @Test
    void stepsUpAndDownOneBlockStayInOnePiece() {
        Zone stairs = new Zone(START, List.of(new BlockBox(0, 0, 0, 3, 3, 0)), true);
        assertEquals(1, StandingArea.pieces(stairs, pos -> pos.getY() == pos.getX()).size(), "a staircase is one piece");
        assertEquals(2, StandingArea.pieces(stairs, pos -> pos.getY() == (pos.getX() < 2 ? 0 : 2)).size(), "a two-block ledge splits");
    }

    /** The start zone and a second zone with a one-block wall line between them at x = 10, so offline they don't connect. */
    private static ZombiesMap separatedZones() {
        return ZombiesMap.EMPTY
                .withZone(new Zone(START, List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true))
                .withZone(new Zone(NEXT, List.of(new BlockBox(11, 0, 0, 19, 3, 9)), false))
                .withFeature(new BlockPos(2, 0, 2), FeatureKind.SPAWN)
                .withFeature(new BlockPos(12, 0, 2), FeatureKind.SPAWN);
    }

    @Test
    void aDoorBetweenTwoZonesMakesTheFarOneReachable() {
        assertTrue(MapValidator.validate(separatedZones()).has(MapValidator.UNREACHABLE_ZONE));
        DoorDef door = DoorDef.empty("gate", 750).withMember(new BlockPos(10, 0, 2)).withMember(new BlockPos(10, 1, 2));
        assertEquals(java.util.Set.of(START, NEXT), door.zones(ZoneIndex.of(separatedZones())));
        ValidationReport report = MapValidator.validate(separatedZones().withDoor(door));
        assertEquals(List.of(), report.problems());
    }

    @Test
    void badDoorsAreReported() {
        ZombiesMap empty = separatedZones().withDoor(DoorDef.empty("nothing", 750));
        assertTrue(MapValidator.validate(empty).has(MapValidator.EMPTY_DOOR));
        ZombiesMap oneSided = separatedZones().withDoor(DoorDef.empty("wall", 750).withMember(new BlockPos(30, 0, 2)).withMember(new BlockPos(5, 5, 5)));
        assertTrue(MapValidator.validate(oneSided).has(MapValidator.DOOR_NOT_BETWEEN_ZONES), "borders only the start zone, from above");
        ZombiesMap pointless = twoTouchingZonesWithSpawns().withDoor(DoorDef.empty("arch", 750).withMember(new BlockPos(9, 4, 2)).withMember(new BlockPos(10, 4, 2)));
        ValidationReport report = MapValidator.validate(pointless);
        assertTrue(report.has(MapValidator.LOOPHOLE), "touching zones are connected anyway: " + report.problems());
        assertFalse(report.playable(), "a loophole stops a game from starting");
        Problem loophole = report.problems().stream().filter(p -> p.code().equals(MapValidator.LOOPHOLE)).findFirst().orElseThrow();
        BlockPos at = loophole.pos().orElseThrow();
        assertTrue(Highlight.plain(loophole.message()).matches("Loophole found between \\w+ and \\w+ at \\(-?\\d+, -?\\d+, -?\\d+\\)"), loophole.message());
        assertTrue(Highlight.plain(loophole.message()).endsWith("(" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")"), "names where the problem marker is");
        assertTrue(ZoneIndex.of(pointless).zoneAt(at).isPresent(), "the opening is where the rooms meet, inside one of them: " + at);
    }
}
