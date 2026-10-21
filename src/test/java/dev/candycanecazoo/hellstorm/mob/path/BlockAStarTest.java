package dev.candycanecazoo.hellstorm.mob.path;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BlockAStarTest {
    private static final MovementClass ZOMBIE = new MovementClass(1, 2);
    private static final int BUDGET = 100_000;

    private static Optional<GridPath> find(TestWorld world, BlockPos start, BlockPos goal) {
        return BlockAStar.DEFAULT.find(world.grid(ZOMBIE), start, goal, BUDGET);
    }

    @Test
    void aStraightCorridorCostsItsLength() {
        GridPath path = find(TestWorld.floorPlan(".........."), new BlockPos(0, 1, 0), new BlockPos(9, 1, 0)).orElseThrow();
        assertEquals(9, path.cost());
        assertEquals(10, path.nodes().size());
        assertEquals(new BlockPos(0, 1, 0), path.nodes().get(0));
        assertEquals(new BlockPos(9, 1, 0), path.nodes().get(9));
    }

    @Test
    void aUTurnWalksAroundTheWall() {
        TestWorld world = TestWorld.floorPlan(
                ".....",
                "####.",
                ".....");
        GridPath path = find(world, new BlockPos(0, 1, 0), new BlockPos(0, 1, 2)).orElseThrow();
        assertEquals(10, path.cost());
        assertTrue(path.nodes().contains(new BlockPos(4, 1, 1)));
    }

    @Test
    void stairsCostTheirRunPlusTheirRiseBothWays() {
        TestWorld world = TestWorld.floorPlan("........")
                .fill(2, 1, 0, 2, 1, 0)
                .fill(3, 1, 0, 3, 2, 0)
                .fill(4, 1, 0, 7, 3, 0);
        BlockPos bottom = new BlockPos(0, 1, 0);
        BlockPos top = new BlockPos(7, 4, 0);
        assertEquals(10, find(world, bottom, top).orElseThrow().cost());
        assertEquals(10, find(world, top, bottom).orElseThrow().cost());
    }

    @Test
    void aMobDropsUpToMaxDropButCannotClimbBack() {
        TestWorld world = TestWorld.floorPlan("......").fill(0, 1, 0, 2, 3, 0);
        BlockPos ledge = new BlockPos(0, 4, 0);
        BlockPos floor = new BlockPos(5, 1, 0);
        assertEquals(8, find(world, ledge, floor).orElseThrow().cost());
        assertEquals(Optional.empty(), find(world, floor, ledge));
    }

    @Test
    void aDropDeeperThanMaxDropIsNotTaken() {
        TestWorld world = TestWorld.floorPlan("......").fill(0, 1, 0, 2, 4, 0);
        BlockPos ledge = new BlockPos(0, 5, 0);
        BlockPos floor = new BlockPos(5, 1, 0);
        PassabilityGrid grid = world.grid(ZOMBIE);
        assertEquals(Optional.empty(), BlockAStar.DEFAULT.find(grid, ledge, floor, BUDGET));
        assertEquals(9, new BlockAStar(BlockAStar.Mode.MANHATTAN, 4).find(grid, ledge, floor, BUDGET).orElseThrow().cost());
    }

    @Test
    void aStepUpNeedsHeadroom() {
        TestWorld world = TestWorld.floorPlan("...").fill(2, 1, 0, 2, 1, 0).fill(1, 3, 0, 1, TestWorld.TOP, 0);
        assertEquals(Optional.empty(), find(world, new BlockPos(0, 1, 0), new BlockPos(2, 2, 0)));
    }

    @Test
    void aWalledOffGoalIsUnreachable() {
        assertEquals(Optional.empty(), find(TestWorld.floorPlan("..#.."), new BlockPos(0, 1, 0), new BlockPos(4, 1, 0)));
    }

    @Test
    void theExpansionBudgetEndsTheSearchEmpty() {
        PassabilityGrid grid = TestWorld.room(30, 30).grid(ZOMBIE);
        BlockPos start = new BlockPos(0, 1, 0);
        BlockPos goal = new BlockPos(29, 1, 29);
        assertEquals(Optional.empty(), BlockAStar.DEFAULT.find(grid, start, goal, 10));
        assertEquals(58, BlockAStar.DEFAULT.find(grid, start, goal, BUDGET).orElseThrow().cost());
    }

    @Test
    void theStartIsItsOwnPath() {
        BlockPos start = new BlockPos(1, 1, 0);
        GridPath path = find(TestWorld.floorPlan("..."), start, start).orElseThrow();
        assertEquals(List.of(start), path.nodes());
        assertEquals(0, path.cost());
    }

    @Test
    void theHeuristicNeverExceedsTheTrueCostOnRandomRooms() {
        for (BlockAStar.Mode mode : BlockAStar.Mode.values()) {
            BlockAStar astar = new BlockAStar(mode, BlockAStar.DEFAULT_MAX_DROP);
            for (long seed = 1; seed <= 6; seed++) {
                PassabilityGrid grid = TestWorld.random(seed, 12).grid(ZOMBIE);
                int pairs = 0;
                for (BlockPos start : TestWorld.walkableNodes(grid)) {
                    Long2IntOpenHashMap costs = Dijkstra.costsFrom(astar, grid, start);
                    for (var entry : costs.long2IntEntrySet()) {
                        BlockPos goal = BlockPos.of(entry.getLongKey());
                        assertTrue(astar.heuristic(start, goal) <= entry.getIntValue(),
                                mode + " seed " + seed + ": h(" + start + ", " + goal + ") > " + entry.getIntValue());
                        pairs++;
                    }
                }
                assertTrue(pairs > 1000, "seed " + seed + " connected only " + pairs + " pairs");
            }
        }
    }

    @Test
    void searchesMatchTheReferenceCostOnRandomRooms() {
        for (BlockAStar.Mode mode : BlockAStar.Mode.values()) {
            BlockAStar astar = new BlockAStar(mode, BlockAStar.DEFAULT_MAX_DROP);
            for (long seed = 1; seed <= 6; seed++) {
                PassabilityGrid grid = TestWorld.random(seed, 12).grid(ZOMBIE);
                List<BlockPos> nodes = TestWorld.walkableNodes(grid);
                Random random = new Random(seed);
                for (int i = 0; i < 100; i++) {
                    BlockPos start = nodes.get(random.nextInt(nodes.size()));
                    BlockPos goal = nodes.get(random.nextInt(nodes.size()));
                    Long2IntOpenHashMap costs = Dijkstra.costsFrom(astar, grid, start);
                    Optional<Integer> found = astar.find(grid, start, goal, BUDGET).map(GridPath::cost);
                    Optional<Integer> expected = costs.containsKey(goal.asLong()) ? Optional.of(costs.get(goal.asLong())) : Optional.empty();
                    assertEquals(expected, found, mode + " seed " + seed + " " + start + " -> " + goal);
                }
            }
        }
    }
}
