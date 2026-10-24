package dev.candycanecazoo.hellstorm.mob.path;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FlowFieldTest {
    /** A floor plan at y = 0: rows run along z, characters along x, '.' is walkable. Straight steps cost 10, one-way drops as given. */
    private record Plan(String[] rows, Map<Long, List<long[]>> extra) {
        static Plan of(String... rows) {
            return new Plan(rows, new HashMap<>());
        }

        static Plan random(long seed, int size) {
            Random random = new Random(seed);
            String[] rows = new String[size];
            for (int z = 0; z < size; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < size; x++) {
                    row.append(random.nextInt(10) < 3 ? '#' : '.');
                }
                rows[z] = row.toString();
            }
            Plan plan = new Plan(rows, new HashMap<>());
            for (int i = 0; i < size; i++) {
                List<BlockPos> cells = plan.cells();
                BlockPos a = cells.get(random.nextInt(cells.size()));
                BlockPos b = cells.get(random.nextInt(cells.size()));
                plan.oneWay(a, b, 5 + random.nextInt(40));
            }
            return plan;
        }

        /** A one-way move, like a drop off a ledge. */
        Plan oneWay(BlockPos from, BlockPos to, int cost) {
            extra.computeIfAbsent(from.asLong(), k -> new ArrayList<>()).add(new long[] {to.asLong(), cost});
            return this;
        }

        boolean open(int x, int z) {
            return z >= 0 && z < rows.length && x >= 0 && x < rows[z].length() && rows[z].charAt(x) == '.';
        }

        List<BlockPos> cells() {
            List<BlockPos> cells = new ArrayList<>();
            for (int z = 0; z < rows.length; z++) {
                for (int x = 0; x < rows[z].length(); x++) {
                    if (open(x, z)) {
                        cells.add(new BlockPos(x, 0, z));
                    }
                }
            }
            return cells;
        }

        List<long[]> moves(BlockPos from) {
            List<long[]> moves = new ArrayList<>();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (open(from.getX() + d[0], from.getZ() + d[1])) {
                    moves.add(new long[] {from.offset(d[0], 0, d[1]).asLong(), 10});
                }
            }
            moves.addAll(extra.getOrDefault(from.asLong(), List.of()));
            return moves;
        }

        FlowGraph graph() {
            FlowGraph.Builder builder = FlowGraph.builder();
            for (BlockPos cell : cells()) {
                builder.node(cell);
                for (long[] move : moves(cell)) {
                    builder.move(cell, BlockPos.of(move[0]), (int) move[1]);
                }
            }
            return builder.build();
        }

        /** Reference walking cost from {@code from} to {@code goal}, by a plain forward Dijkstra. */
        int cost(BlockPos from, BlockPos goal) {
            Map<Long, Integer> best = new HashMap<>();
            PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[1], b[1]));
            queue.add(new long[] {from.asLong(), 0});
            best.put(from.asLong(), 0);
            while (!queue.isEmpty()) {
                long[] at = queue.poll();
                if (at[1] > best.get(at[0])) {
                    continue;
                }
                if (at[0] == goal.asLong()) {
                    return (int) at[1];
                }
                for (long[] move : moves(BlockPos.of(at[0]))) {
                    int next = (int) (at[1] + move[1]);
                    if (next < best.getOrDefault(move[0], Integer.MAX_VALUE)) {
                        best.put(move[0], next);
                        queue.add(new long[] {move[0], next});
                    }
                }
            }
            return FlowField.UNREACHABLE;
        }
    }

    /** Every cell on the downhill walk from {@code from}, not including it. */
    private static List<BlockPos> descent(FlowField field, BlockPos from, int tieBreak) {
        List<BlockPos> cells = new ArrayList<>();
        for (Optional<BlockPos> next = field.next(from, tieBreak); next.isPresent(); next = field.next(next.get(), tieBreak)) {
            cells.add(next.get());
        }
        return cells;
    }

    @Test
    void distancesAreTheTrueWalkingCostToTheGoal() {
        for (long seed = 1; seed <= 6; seed++) {
            Plan plan = Plan.random(seed, 14);
            List<BlockPos> cells = plan.cells();
            BlockPos goal = cells.get(new Random(seed).nextInt(cells.size()));
            FlowField field = plan.graph().fieldTo(goal).orElseThrow();
            for (BlockPos from : cells) {
                assertEquals(plan.cost(from, goal), field.distance(from), "seed " + seed + " from " + from.toShortString());
            }
        }
    }

    @Test
    void stepsAlwaysGoDownhillAndReachTheGoal() {
        Plan plan = Plan.random(9, 16);
        List<BlockPos> cells = plan.cells();
        BlockPos goal = cells.get(0);
        FlowField field = plan.graph().fieldTo(goal).orElseThrow();
        for (BlockPos from : cells) {
            if (field.distance(from) == FlowField.UNREACHABLE) {
                continue;
            }
            List<BlockPos> walk = descent(field, from, 3);
            BlockPos end = walk.isEmpty() ? from : walk.get(walk.size() - 1);
            assertEquals(goal, end, "descent from " + from.toShortString() + " should end at the goal");
            int previous = field.distance(from);
            for (BlockPos cell : walk) {
                assertTrue(field.distance(cell) < previous, "distance must strictly fall along the descent");
                previous = field.distance(cell);
            }
        }
    }

    /** A wall with one gap: the walk goes round through the gap. */
    @Test
    void theFieldRoutesAroundWalls() {
        Plan plan = Plan.of(
                "..........",
                "..........",
                "#########.",
                "..........",
                "..........");
        FlowField field = plan.graph().fieldTo(new BlockPos(0, 0, 4)).orElseThrow();
        assertEquals(10 * (9 + 4 + 9), field.distance(new BlockPos(0, 0, 0)), "around the end of the wall at x = 9");
        assertTrue(descent(field, new BlockPos(0, 0, 0), 0).contains(new BlockPos(9, 0, 2)), "through the gap");
    }

    @Test
    void anUnreachableGoalOrCellReadsAsUnreachable() {
        Plan plan = Plan.of(
                "..#..",
                "..#..");
        FlowGraph graph = plan.graph();
        FlowField field = graph.fieldTo(new BlockPos(4, 0, 0)).orElseThrow();
        assertEquals(FlowField.UNREACHABLE, field.distance(new BlockPos(0, 0, 0)));
        assertEquals(Optional.empty(), field.next(new BlockPos(0, 0, 0), 0));
        assertEquals(Optional.empty(), field.ahead(new BlockPos(0, 0, 0), 50, 0));
        assertEquals(Optional.empty(), graph.fieldTo(new BlockPos(2, 0, 0)), "a wall cell isn't a goal");
    }

    @Test
    void tieBreaksSpreadZombiesAcrossEquallyGoodSteps() {
        String[] rows = new String[9];
        Arrays.fill(rows, ".".repeat(9));
        FlowField field = Plan.of(rows).graph().fieldTo(new BlockPos(8, 0, 8)).orElseThrow();
        assertNotEquals(field.next(new BlockPos(0, 0, 0), 0), field.next(new BlockPos(0, 0, 0), 1),
                "from the far corner, going +x or +z first is equally short");
    }

    /** The point a far zombie walks to: the given walking cost down the field, round the wall rather than through it. */
    @Test
    void aheadIsTheGivenWalkDownTheField() {
        Plan plan = Plan.of(
                "..........",
                "..........",
                "#########.",
                "..........",
                "..........");
        FlowField field = plan.graph().fieldTo(new BlockPos(0, 0, 4)).orElseThrow();
        BlockPos from = new BlockPos(0, 0, 0);
        BlockPos point = field.ahead(from, 60, 0).orElseThrow();
        assertEquals(field.distance(from) - 60, field.distance(point), "six blocks nearer the goal along the walk");
        assertTrue(point.getZ() <= 1, "still on the near side of the wall, heading for the gap: " + point.toShortString());
        assertEquals(new BlockPos(0, 0, 4), field.ahead(new BlockPos(0, 0, 3), 60, 0).orElseThrow(), "no farther than the goal");
    }
}
