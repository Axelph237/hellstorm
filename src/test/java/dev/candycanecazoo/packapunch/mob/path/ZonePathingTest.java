package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.CellProbe;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    void aFlowFieldOverTheUnionGridLeadsThroughTheDoorway() {
        ZombiesMap map = rooms(6);
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), PROBE, 2);
        PassabilityGrid grid = ZonePathing.unionGrid(map, Set.of(WEST, EAST), passages, WORLD, ZOMBIE);
        FlowField field = FlowGraph.build(grid, BlockAStar.DEFAULT).fieldTo(new BlockPos(10, 0, 4)).orElseThrow();
        List<BlockPos> walk = field.descent(new BlockPos(0, 0, 0), 100, 0);
        assertTrue(walk.contains(new BlockPos(5, 0, 2)), "the walk goes through the doorway: " + walk);
        assertEquals(new BlockPos(10, 0, 4), walk.get(walk.size() - 1));
    }

    @Test
    void aZoneLeftOutOfTheUnionIsUnreachable() {
        ZombiesMap map = rooms(6);
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), PROBE, 2);
        PassabilityGrid grid = ZonePathing.unionGrid(map, Set.of(EAST), passages, WORLD, ZOMBIE);
        FlowField field = FlowGraph.build(grid, BlockAStar.DEFAULT).fieldTo(new BlockPos(10, 0, 4)).orElseThrow();
        assertEquals(FlowField.UNREACHABLE, field.distance(new BlockPos(0, 0, 0)), "a locked zone isn't part of the field");
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
        FlowField field = FlowGraph.build(ZonePathing.unionGrid(map, Set.of(WEST, EAST), passages, world, ZOMBIE), BlockAStar.DEFAULT)
                .fieldTo(new BlockPos(10, 0, 4)).orElseThrow();
        assertTrue(field.distance(new BlockPos(0, 0, 0)) < FlowField.UNREACHABLE, "west reaches east through the doorway, not around the wall");
        assertTrue(field.descent(new BlockPos(0, 0, 0), 100, 0).contains(new BlockPos(5, 0, 2)));
    }
}
