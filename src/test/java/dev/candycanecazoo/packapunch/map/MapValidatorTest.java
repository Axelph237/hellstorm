package dev.candycanecazoo.packapunch.map;

import dev.candycanecazoo.packapunch.map.validate.MapValidator;
import dev.candycanecazoo.packapunch.map.validate.ValidationReport;
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
                .withFeature(new BlockPos(12, 0, -3), FeatureKind.SPAWN);
        ValidationReport report = MapValidator.validate(map, new dev.candycanecazoo.packapunch.map.validate.ValidationContext(
                CellProbe.ALL_SOLID, 2, new dev.candycanecazoo.packapunch.config.GameSettings.Windows(5, 4, 30, 15, 6.0),
                List.of(new dev.candycanecazoo.packapunch.map.validate.ValidationContext.MobSize("test:giant", 3.6f, 12f))));
        assertTrue(report.has(MapValidator.WINDOW_TOO_LARGE), "8 wide is over 5");
        assertTrue(report.has(MapValidator.WINDOW_UNKNOWN_ZONE));
        assertTrue(report.problems().stream().anyMatch(p -> p.code().equals(MapValidator.WINDOW_WITHOUT_SPAWN) && p.message().contains("'huge'")));
        assertFalse(report.problems().stream().anyMatch(p -> p.code().equals(MapValidator.WINDOW_WITHOUT_SPAWN) && p.message().contains("'lost'")),
                "the marker in front of 'lost' links to it");
        assertTrue(report.has(MapValidator.MOB_TOO_BIG_FOR_WINDOW));
    }
}
