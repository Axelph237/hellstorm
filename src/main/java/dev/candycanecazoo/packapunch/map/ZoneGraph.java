package dev.candycanecazoo.packapunch.map;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which zones connect to which, from the passages found in the world ({@link Passages}). Boxes touching isn't enough:
 * a bought door's blocks often sit inside the zones' boxes, and the zones stay apart until it opens.
 */
public final class ZoneGraph {
    private final Map<ZoneId, Set<ZoneId>> edges = new TreeMap<>();

    private ZoneGraph() {}

    public static ZoneGraph of(ZombiesMap map, Set<Passages.Link> passages) {
        ZoneGraph graph = new ZoneGraph();
        map.zones().keySet().forEach(id -> graph.edges.put(id, new TreeSet<>()));
        for (Passages.Link link : passages) {
            if (graph.edges.containsKey(link.a()) && graph.edges.containsKey(link.b())) {
                graph.connect(link.a(), link.b());
            }
        }
        return graph;
    }

    private void connect(ZoneId a, ZoneId b) {
        edges.get(a).add(b);
        edges.get(b).add(a);
    }

    public Set<ZoneId> neighbors(ZoneId zone) {
        return edges.getOrDefault(zone, Set.of());
    }

    public Set<ZoneId> reachableFrom(Collection<ZoneId> starts) {
        Set<ZoneId> seen = new HashSet<>();
        Deque<ZoneId> queue = new ArrayDeque<>();
        for (ZoneId start : starts) {
            if (edges.containsKey(start) && seen.add(start)) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            for (ZoneId next : neighbors(queue.poll())) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }
}
