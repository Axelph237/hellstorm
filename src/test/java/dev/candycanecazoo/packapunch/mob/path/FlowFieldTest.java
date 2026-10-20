package dev.candycanecazoo.packapunch.mob.path;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FlowFieldTest {
    private static final MovementClass ZOMBIE = new MovementClass(1, 2);

    @Test
    void distancesAreTheTrueWalkingCostToTheGoal() {
        for (long seed = 1; seed <= 6; seed++) {
            PassabilityGrid grid = TestWorld.random(seed, 14).grid(ZOMBIE);
            FlowGraph graph = FlowGraph.build(grid, BlockAStar.DEFAULT);
            List<BlockPos> nodes = TestWorld.walkableNodes(grid);
            BlockPos goal = nodes.get(new Random(seed).nextInt(nodes.size()));
            FlowField field = graph.fieldTo(goal).orElseThrow();
            for (BlockPos from : nodes) {
                Long2IntOpenHashMap reference = Dijkstra.costsFrom(BlockAStar.DEFAULT, grid, from);
                int expected = reference.containsKey(goal.asLong()) ? reference.get(goal.asLong()) : FlowField.UNREACHABLE;
                assertEquals(expected, field.distance(from), "seed " + seed + " from " + from.toShortString());
            }
        }
    }

    @Test
    void stepsAlwaysGoDownhillAndReachTheGoal() {
        PassabilityGrid grid = TestWorld.random(9, 16).grid(ZOMBIE);
        FlowGraph graph = FlowGraph.build(grid, BlockAStar.DEFAULT);
        List<BlockPos> nodes = TestWorld.walkableNodes(grid);
        BlockPos goal = nodes.get(0);
        FlowField field = graph.fieldTo(goal).orElseThrow();
        for (BlockPos from : nodes) {
            if (field.distance(from) == FlowField.UNREACHABLE) {
                continue;
            }
            List<BlockPos> walk = field.descent(from, 10_000, 3);
            BlockPos end = walk.isEmpty() ? from : walk.get(walk.size() - 1);
            assertEquals(goal, end, "descent from " + from.toShortString() + " should end at the goal");
            int previous = field.distance(from);
            for (BlockPos cell : walk) {
                assertTrue(field.distance(cell) < previous, "distance must strictly fall along the descent");
                previous = field.distance(cell);
            }
        }
    }

    /** A wall with one gap: the flow goes around through the gap. */
    @Test
    void theFieldRoutesAroundWalls() {
        TestWorld world = TestWorld.floorPlan(
                "..........",
                "..........",
                "#########.",
                "..........",
                "..........");
        FlowGraph graph = FlowGraph.build(world.grid(ZOMBIE), BlockAStar.DEFAULT);
        FlowField field = graph.fieldTo(new BlockPos(0, 1, 4)).orElseThrow();
        assertEquals(9 + 4 + 9, field.distance(new BlockPos(0, 1, 0)), "around the end of the wall at x = 9");
        assertTrue(field.descent(new BlockPos(0, 1, 0), 100, 0).contains(new BlockPos(9, 1, 2)), "through the gap");
    }

    @Test
    void anUnreachableGoalOrCellReadsAsUnreachable() {
        TestWorld world = TestWorld.floorPlan(
                "..#..",
                "..#..");
        FlowGraph graph = FlowGraph.build(world.grid(ZOMBIE), BlockAStar.DEFAULT);
        FlowField field = graph.fieldTo(new BlockPos(4, 1, 0)).orElseThrow();
        assertEquals(FlowField.UNREACHABLE, field.distance(new BlockPos(0, 1, 0)));
        assertEquals(Optional.empty(), field.next(new BlockPos(0, 1, 0), 0));
        assertEquals(Optional.empty(), graph.fieldTo(new BlockPos(2, 1, 0)), "a wall cell isn't a goal");
    }

    @Test
    void tieBreaksSpreadZombiesAcrossEquallyGoodSteps() {
        FlowGraph graph = FlowGraph.build(TestWorld.room(9, 9).grid(ZOMBIE), BlockAStar.DEFAULT);
        FlowField field = graph.fieldTo(new BlockPos(8, 1, 8)).orElseThrow();
        assertNotEquals(field.next(new BlockPos(0, 1, 0), 0), field.next(new BlockPos(0, 1, 0), 1),
                "from the far corner, going +x or +z first is equally short");
    }
}
