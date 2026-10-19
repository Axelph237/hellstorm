package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.ZoneId;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Plans block paths across zones for one movement class. The high-level graph's nodes are portal cells; edges are
 * cached walks between the portal cells of one zone plus the crossings through each portal. {@link #plan} may run on
 * any thread, concurrently with {@link #addPortal}: it reads one immutable snapshot of the portals.
 */
public final class HierarchicalPlanner {
    private record End(ZoneId zone, BlockPos cell) {}

    private record Crossing(End to, int cost) {}

    private record Graph(Set<String> portalIds, Map<ZoneId, List<End>> ends, Map<End, List<Crossing>> crossings) {
        static final Graph EMPTY = new Graph(Set.of(), Map.of(), Map.of());

        List<End> endsIn(ZoneId zone) {
            return ends.getOrDefault(zone, List.of());
        }

        Graph with(Portal portal) {
            End a = new End(portal.zoneA(), portal.cellA());
            End b = new End(portal.zoneB(), portal.cellB());
            Set<String> ids = new HashSet<>(portalIds);
            ids.add(portal.id());
            Map<ZoneId, List<End>> nextEnds = new HashMap<>(ends);
            appendNew(nextEnds, a.zone(), a);
            appendNew(nextEnds, b.zone(), b);
            Map<End, List<Crossing>> nextCrossings = new HashMap<>(crossings);
            appendNew(nextCrossings, a, new Crossing(b, portal.crossingCost()));
            appendNew(nextCrossings, b, new Crossing(a, portal.crossingCost()));
            return new Graph(Set.copyOf(ids), Map.copyOf(nextEnds), Map.copyOf(nextCrossings));
        }

        private static <K, V> void appendNew(Map<K, List<V>> map, K key, V value) {
            List<V> list = new ArrayList<>(map.getOrDefault(key, List.of()));
            if (!list.contains(value)) {
                list.add(value);
            }
            map.put(key, List.copyOf(list));
        }
    }

    private enum Role { START, PORTAL, GOAL }

    private record Node(Role role, End at) {}

    /** {@code nodes} runs from the edge's source cell to its target cell inclusive. */
    private record Edge(Node to, int cost, List<BlockPos> nodes) {}

    private record Step(Node from, List<BlockPos> nodes) {}

    private record Open(int f, int order, Node node) {}

    private final Map<ZoneId, PassabilityGrid> grids;
    private final PortalPathCache cache;
    private volatile Graph graph = Graph.EMPTY;

    public HierarchicalPlanner(Map<ZoneId, PassabilityGrid> grids, PortalPathCache cache) {
        if (grids.values().stream().map(PassabilityGrid::movementClass).distinct().count() > 1) {
            throw new IllegalArgumentException("A planner's grids must share one movement class");
        }
        this.grids = Map.copyOf(grids);
        this.cache = cache;
    }

    /**
     * Registers an opened portal and computes its walks to every other portal in both of its zones, so the cost lands
     * when the door opens rather than on the first plan. Existing edges are reused. Adding a portal twice does nothing.
     */
    public synchronized void addPortal(Portal portal) {
        Graph before = graph;
        if (before.portalIds().contains(portal.id())) {
            return;
        }
        connect(before, new End(portal.zoneA(), portal.cellA()));
        connect(before, new End(portal.zoneB(), portal.cellB()));
        graph = before.with(portal);
    }

    private void connect(Graph before, End end) {
        PassabilityGrid grid = grids.get(end.zone());
        if (grid == null) {
            return;
        }
        for (End other : before.endsIn(end.zone())) {
            if (!other.equals(end)) {
                cache.edge(grid, end.zone(), end.cell(), other.cell());
                cache.edge(grid, end.zone(), other.cell(), end.cell());
            }
        }
    }

    /** The cheapest block path from start to goal inclusive, through open portals when the zones differ. */
    public Optional<List<BlockPos>> plan(BlockPos start, ZoneId startZone, BlockPos goal, ZoneId goalZone) {
        PassabilityGrid startGrid = grids.get(startZone);
        if (startGrid == null || !grids.containsKey(goalZone)) {
            return Optional.empty();
        }
        if (startZone.equals(goalZone)) {
            return local(startGrid, start, goal).map(GridPath::nodes);
        }
        return new Query(graph, new End(startZone, start), new End(goalZone, goal)).run();
    }

    private Optional<GridPath> local(PassabilityGrid grid, BlockPos from, BlockPos to) {
        return cache.astar().find(grid, from, to, cache.maxExpanded());
    }

    private final class Query {
        private final Graph graph;
        private final End start;
        private final End goal;

        Query(Graph graph, End start, End goal) {
            this.graph = graph;
            this.start = start;
            this.goal = goal;
        }

        Optional<List<BlockPos>> run() {
            Node startNode = new Node(Role.START, start);
            Map<Node, Integer> best = new HashMap<>();
            Map<Node, Step> via = new HashMap<>();
            Set<Node> closed = new HashSet<>();
            PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingInt(Open::f).thenComparingInt(Open::order));
            best.put(startNode, 0);
            open.add(new Open(estimate(startNode), 0, startNode));
            int order = 1;
            while (!open.isEmpty()) {
                Node node = open.poll().node();
                if (!closed.add(node)) {
                    continue;
                }
                if (node.role() == Role.GOAL) {
                    return Optional.of(trace(via, node));
                }
                int base = best.get(node);
                for (Edge edge : edgesFrom(node)) {
                    int g = base + edge.cost();
                    if (g < best.getOrDefault(edge.to(), Integer.MAX_VALUE)) {
                        best.put(edge.to(), g);
                        via.put(edge.to(), new Step(node, edge.nodes()));
                        open.add(new Open(g + estimate(edge.to()), order++, edge.to()));
                    }
                }
            }
            return Optional.empty();
        }

        private int estimate(Node node) {
            return cache.astar().heuristic(node.at().cell(), goal.cell());
        }

        private List<Edge> edgesFrom(Node node) {
            List<Edge> edges = new ArrayList<>();
            End at = node.at();
            PassabilityGrid grid = grids.get(at.zone());
            if (node.role() == Role.START) {
                for (End end : graph.endsIn(at.zone())) {
                    local(grid, at.cell(), end.cell()).ifPresent(path -> edges.add(edge(Role.PORTAL, end, path)));
                }
                return edges;
            }
            if (grid != null) {
                for (End other : graph.endsIn(at.zone())) {
                    if (!other.equals(at)) {
                        cache.edge(grid, at.zone(), at.cell(), other.cell()).ifPresent(path -> edges.add(edge(Role.PORTAL, other, path)));
                    }
                }
                if (at.zone().equals(goal.zone())) {
                    local(grid, at.cell(), goal.cell()).ifPresent(path -> edges.add(edge(Role.GOAL, goal, path)));
                }
            }
            for (Crossing crossing : graph.crossings().getOrDefault(at, List.of())) {
                edges.add(new Edge(new Node(Role.PORTAL, crossing.to()), crossing.cost(), List.of(at.cell(), crossing.to().cell())));
            }
            return edges;
        }

        private static Edge edge(Role role, End to, GridPath path) {
            return new Edge(new Node(role, to), path.cost(), path.nodes());
        }

        private static List<BlockPos> trace(Map<Node, Step> via, Node goalNode) {
            List<List<BlockPos>> legs = new ArrayList<>();
            for (Step step = via.get(goalNode); step != null; step = via.get(step.from())) {
                legs.add(step.nodes());
            }
            Collections.reverse(legs);
            List<BlockPos> nodes = new ArrayList<>(legs.get(0));
            for (List<BlockPos> leg : legs.subList(1, legs.size())) {
                nodes.addAll(leg.subList(1, leg.size()));
            }
            return List.copyOf(nodes);
        }
    }
}
