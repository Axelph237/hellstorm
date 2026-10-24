package dev.candycanecazoo.hellstorm.mob.path;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Walking cost from every node of a {@link FlowGraph} to one goal. Any number of zombies chasing the same player read the
 * same field, each from wherever it stands. Immutable.
 */
public final class FlowField {
    public static final int UNREACHABLE = Integer.MAX_VALUE;

    private final FlowGraph graph;
    private final int[] distance;

    FlowField(FlowGraph graph, int[] distance) {
        this.graph = graph;
        this.distance = distance;
    }

    /** Walking cost from {@code pos} to the goal, or {@link #UNREACHABLE} (also for cells off the grid). */
    public int distance(BlockPos pos) {
        int node = graph.indexOf(pos);
        return node < 0 ? UNREACHABLE : distance[node];
    }

    /**
     * The neighbour that is cheapest to go through: least move cost plus remaining distance. {@code tieBreak} chooses
     * among equally good neighbours, so zombies with different values fan out. Empty at the goal or when unreachable.
     */
    public Optional<BlockPos> next(BlockPos from, int tieBreak) {
        int node = graph.indexOf(from);
        if (node < 0 || distance[node] == UNREACHABLE || distance[node] == 0) {
            return Optional.empty();
        }
        int best = UNREACHABLE;
        List<Integer> ties = new ArrayList<>(4);
        for (int e = graph.forwardStart(node); e < graph.forwardEnd(node); e++) {
            int to = graph.forwardTo(e);
            if (distance[to] == UNREACHABLE) {
                continue;
            }
            int through = graph.forwardCost(e) + distance[to];
            if (through < best) {
                best = through;
                ties.clear();
                ties.add(to);
            } else if (through == best) {
                ties.add(to);
            }
        }
        return ties.isEmpty() ? Optional.empty() : Optional.of(graph.position(ties.get(Math.floorMod(tieBreak, ties.size()))));
    }

    /**
     * The first cell on the downhill walk from {@code from} that is at least {@code cost} nearer the goal, or the goal itself
     * if that is nearer. Empty at the goal or when unreachable.
     */
    public Optional<BlockPos> ahead(BlockPos from, int cost, int tieBreak) {
        int start = distance(from);
        if (start == UNREACHABLE || start == 0) {
            return Optional.empty();
        }
        BlockPos at = from;
        while (distance(at) > start - cost) {
            Optional<BlockPos> next = next(at, tieBreak);
            if (next.isEmpty()) {
                break;
            }
            at = next.get();
        }
        return Optional.of(at);
    }
}
