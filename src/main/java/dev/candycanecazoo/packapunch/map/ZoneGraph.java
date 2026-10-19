package dev.candycanecazoo.packapunch.map;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which zones connect to which: zones whose boxes share a face, plus passages found through the world
 * ({@link Passages}). Pack-a-Punch doors add edges once they exist (phase 5).
 */
public final class ZoneGraph {
    private final Map<ZoneId, Set<ZoneId>> edges = new TreeMap<>();

    private ZoneGraph() {}

    public static ZoneGraph of(ZombiesMap map, Set<Passages.Link> passages) {
        ZoneGraph graph = new ZoneGraph();
        List<Zone> zones = List.copyOf(map.zones().values());
        zones.forEach(z -> graph.edges.put(z.id(), new TreeSet<>()));
        for (int i = 0; i < zones.size(); i++) {
            for (int j = i + 1; j < zones.size(); j++) {
                if (zones.get(i).touches(zones.get(j))) {
                    graph.connect(zones.get(i).id(), zones.get(j).id());
                }
            }
        }
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
