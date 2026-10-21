package dev.candycanecazoo.hellstorm.mob.path;

import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.CellProbe;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two 5x2x5 rooms on a floor at y = -1: west at x 0..4, east at x 6..10, with a wall at x = 5 that has a 1x2 doorway at
 * z = 2. Everything at y >= 0 outside the wall is open air; the floor is solid.
 */
class ZonePathingTest {
    private static final ZoneId WEST = new ZoneId("west");
    private static final ZoneId EAST = new ZoneId("east");
    private static final MovementClass ZOMBIE = new MovementClass(1, 2);

    private static boolean wall(int x, int y, int z) {
        return x == 5 && !(z == 2 && (y == 0 || y == 1));
    }

    private static final CellQuery WORLD = (x, y, z) -> y < 0 || wall(x, y, z) ? CellQuery.Cell.SOLID : CellQuery.Cell.OPEN;
    private static final CellProbe PROBE = pos -> WORLD.at(pos.getX(), pos.getY(), pos.getZ()) == CellQuery.Cell.OPEN
            ? CellProbe.Cell.OPEN : CellProbe.Cell.SOLID;

    private static ZombiesMap rooms(int eastMinX) {
        return ZombiesMap.EMPTY
                .withZone(new Zone(WEST, List.of(new BlockBox(0, 0, 0, 4, 1, 4)), true))
                .withZone(new Zone(EAST, List.of(new BlockBox(eastMinX, 0, 0, eastMinX + 4, 1, 4)), false));
    }

    @Test
    void aDoorwayBecomesAZeroCostPortalOnItsFloorCell() {
        ZombiesMap map = rooms(6);
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), PROBE, 2);
        Map<ZoneId, PassabilityGrid> grids = ZonePathing.grids(map, passages, WORLD, ZOMBIE);
        List<Portal> portals = ZonePathing.portals(passages, grids);
        assertEquals(1, portals.size(), portals::toString);
        Portal portal = portals.get(0);
        assertEquals(new BlockPos(5, 0, 2), portal.cellA());
        assertEquals(portal.cellA(), portal.cellB());
        assertEquals(0, portal.crossingCost());
    }

    @Test
    void touchingZonesGetAPortalBetweenAdjacentCells() {
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(WEST, List.of(new BlockBox(0, 0, 0, 4, 1, 4)), true))
                .withZone(new Zone(EAST, List.of(new BlockBox(5, 0, 0, 9, 1, 4)), false));
        // Enclosed: only the two rooms' own cells are open, so the zones meet face to face with no gap between them.
        CellQuery open = (x, y, z) -> x >= 0 && x <= 9 && y >= 0 && y <= 1 && z >= 0 && z <= 4 ? CellQuery.Cell.OPEN : CellQuery.Cell.SOLID;
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map),
                pos -> open.at(pos.getX(), pos.getY(), pos.getZ()) == CellQuery.Cell.OPEN ? CellProbe.Cell.OPEN : CellProbe.Cell.SOLID, 2);
        List<Portal> portals = ZonePathing.portals(passages, ZonePathing.grids(map, passages, open, ZOMBIE));
        assertEquals(1, portals.size());
        assertEquals(1, portals.get(0).crossingCost());
        assertEquals(1, Math.abs(portals.get(0).cellA().getX() - portals.get(0).cellB().getX()));
    }

    @Test
    void aMobTooBigForTheDoorwayGetsNoPortal() {
        ZombiesMap map = rooms(6);
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), PROBE, 2);
        assertEquals(List.of(), ZonePathing.portals(passages, ZonePathing.grids(map, passages, WORLD, new MovementClass(1, 3))),
                "a 3-tall mob can't use a 2-tall doorway");
    }

    /** A wall that stops short leaves open floor around its ends. Those cells are shared gap, but they aren't the doorway. */
    @Test
    void openAirAroundAWallsEndsIsNotMistakenForTheDoorway() {
        CellQuery world = (x, y, z) -> y < 0 || (x == 5 && z >= 0 && z <= 4 && !(z == 2 && y <= 1)) ? CellQuery.Cell.SOLID : CellQuery.Cell.OPEN;
        CellProbe probe = pos -> world.at(pos.getX(), pos.getY(), pos.getZ()) == CellQuery.Cell.OPEN ? CellProbe.Cell.OPEN : CellProbe.Cell.SOLID;
        ZombiesMap map = rooms(6);
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), probe, 2);
        Map<ZoneId, PassabilityGrid> grids = ZonePathing.grids(map, passages, world, ZOMBIE);
        List<Portal> portals = ZonePathing.portals(passages, grids);
        assertEquals(List.of(new BlockPos(5, 0, 2)), portals.stream().map(Portal::cellA).toList());
    }
}
