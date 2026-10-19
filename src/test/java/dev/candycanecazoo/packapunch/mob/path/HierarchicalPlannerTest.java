package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.ZoneId;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two long rooms side by side, "west" (x 0..9) and "east" (x 11..20), both z 6..35, with a hall (z 0..4) across their
 * north ends. The west-east door is at the far south end; the hall doors are at the north end, near the test's start
 * and goal. Going through the hall crosses two portals but walks 20 blocks; the direct door crosses one and walks 62.
 */
class HierarchicalPlannerTest {
    private static final MovementClass ZOMBIE = new MovementClass(1, 2);
    private static final ZoneId WEST = new ZoneId("west");
    private static final ZoneId EAST = new ZoneId("east");
    private static final ZoneId HALL = new ZoneId("hall");
    private static final BlockBox WEST_BOX = new BlockBox(0, 1, 6, 9, 3, 35);
    private static final BlockBox EAST_BOX = new BlockBox(11, 1, 6, 20, 3, 35);
    private static final BlockBox HALL_BOX = new BlockBox(0, 1, 0, 20, 3, 4);

    private static final Portal SOUTH_DOOR = new Portal("south_door", WEST, new BlockPos(9, 1, 35), EAST, new BlockPos(11, 1, 35), 2);
    private static final Portal WEST_HALL_DOOR = new Portal("west_hall_door", WEST, new BlockPos(5, 1, 6), HALL, new BlockPos(5, 1, 4), 2);
    private static final Portal EAST_HALL_DOOR = new Portal("east_hall_door", HALL, new BlockPos(15, 1, 4), EAST, new BlockPos(15, 1, 6), 2);

    private static final BlockPos START = new BlockPos(5, 1, 9);
    private static final BlockPos GOAL = new BlockPos(15, 1, 9);

    private static final TestWorld WORLD = new TestWorld()
            .open(0, 1, 6, 9, 3, 35)
            .open(11, 1, 6, 20, 3, 35)
            .open(0, 1, 0, 20, 3, 4)
            .open(10, 1, 35, 10, 2, 35)
            .open(5, 1, 5, 5, 2, 5)
            .open(15, 1, 5, 15, 2, 5);

    private static PortalPathCache cache() {
        return new PortalPathCache(BlockAStar.DEFAULT, 100_000);
    }

    private static HierarchicalPlanner planner(PortalPathCache cache, Portal... portals) {
        HierarchicalPlanner planner = new HierarchicalPlanner(Map.of(
                WEST, WORLD.grid(WEST_BOX, ZOMBIE),
                EAST, WORLD.grid(EAST_BOX, ZOMBIE),
                HALL, WORLD.grid(HALL_BOX, ZOMBIE)), cache);
        for (Portal portal : portals) {
            planner.addPortal(portal);
        }
        return planner;
    }

    private static int walked(List<BlockPos> path) {
        int blocks = 0;
        for (int i = 1; i < path.size(); i++) {
            blocks += path.get(i).distManhattan(path.get(i - 1));
        }
        return blocks;
    }

    @Test
    void theShorterWalkWinsOverFewerPortals() {
        List<BlockPos> direct = planner(cache(), SOUTH_DOOR).plan(START, WEST, GOAL, EAST).orElseThrow();
        assertEquals(62, walked(direct));

        List<BlockPos> path = planner(cache(), SOUTH_DOOR, WEST_HALL_DOOR, EAST_HALL_DOOR).plan(START, WEST, GOAL, EAST).orElseThrow();
        assertEquals(20, walked(path));
        assertEquals(START, path.get(0));
        assertEquals(GOAL, path.get(path.size() - 1));
        assertTrue(path.contains(new BlockPos(10, 1, 4)), "walks the hall");
        assertTrue(path.stream().noneMatch(p -> p.getZ() > 9), "never heads south");
    }

    @Test
    void addingAPortalComputesOnlyTheNewEdges() {
        PortalPathCache cache = cache();
        HierarchicalPlanner planner = planner(cache, SOUTH_DOOR, WEST_HALL_DOOR);
        assertEquals(2, cache.searches(), "both directions between the two west portals");
        assertEquals(62, walked(planner.plan(START, WEST, GOAL, EAST).orElseThrow()));
        assertEquals(2, cache.searches(), "planning reuses the cached edges");

        planner.addPortal(EAST_HALL_DOOR);
        assertEquals(6, cache.searches(), "two new edges in the hall and two in the east room, none recomputed");
        assertEquals(20, walked(planner.plan(START, WEST, GOAL, EAST).orElseThrow()));
        assertEquals(32, walked(planner.plan(START, WEST, SOUTH_DOOR.cellB(), EAST).orElseThrow()), "still through the south door");
        assertEquals(6, cache.searches(), "re-planning old and new routes runs no edge searches");

        planner.addPortal(EAST_HALL_DOOR);
        assertEquals(6, cache.searches(), "adding the same portal again is a no-op");
    }

    @Test
    void movementClassesNeverShareCachedEdges() {
        TestWorld world = TestWorld.floorPlan(
                "......",
                "......",
                "###.##",
                "......",
                "......");
        BlockBox zone = new BlockBox(0, 1, 0, 5, TestWorld.TOP, 4);
        BlockPos from = new BlockPos(0, 1, 0);
        BlockPos to = new BlockPos(0, 1, 3);
        PortalPathCache cache = cache();
        assertTrue(cache.edge(world.grid(zone, new MovementClass(1, 2)), WEST, from, to).isPresent());
        assertEquals(Optional.empty(), cache.edge(world.grid(zone, new MovementClass(2, 2)), WEST, from, to));
        assertEquals(2, cache.searches());
    }

    @Test
    void aSameZoneGoalIsOneBlockSearch() {
        BlockPos goal = new BlockPos(0, 1, 30);
        List<BlockPos> path = planner(cache(), SOUTH_DOOR).plan(START, WEST, goal, WEST).orElseThrow();
        assertEquals(START.distManhattan(goal), walked(path));
    }

    @Test
    void noOpenPortalMeansNoPath() {
        assertEquals(Optional.empty(), planner(cache()).plan(START, WEST, GOAL, EAST));
        assertEquals(Optional.empty(), planner(cache(), WEST_HALL_DOOR).plan(START, WEST, GOAL, EAST));
    }
}
