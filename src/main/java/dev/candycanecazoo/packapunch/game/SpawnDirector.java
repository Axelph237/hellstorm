package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.map.ZoneGraph;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Chooses where the next zombie spawns: a marker near the players, in their zones or zones close to them. */
public final class SpawnDirector {
    public record Candidate(BlockPos marker, double weight) {}

    private SpawnDirector() {}

    /** Each player's zone plus unlocked zones within {@code hops} connections of it. */
    public static Set<ZoneId> activeZones(Collection<ZoneId> playerZones, ZoneGraph graph, Set<ZoneId> unlocked, int hops) {
        Set<ZoneId> active = new HashSet<>();
        Map<ZoneId, Integer> depth = new HashMap<>();
        Deque<ZoneId> queue = new ArrayDeque<>();
        for (ZoneId zone : playerZones) {
            if (unlocked.contains(zone) && depth.putIfAbsent(zone, 0) == null) {
                queue.add(zone);
            }
        }
        while (!queue.isEmpty()) {
            ZoneId zone = queue.poll();
            active.add(zone);
            int d = depth.get(zone);
            if (d >= hops) {
                continue;
            }
            for (ZoneId next : graph.neighbors(zone)) {
                if (unlocked.contains(next) && depth.putIfAbsent(next, d + 1) == null) {
                    queue.add(next);
                }
            }
        }
        return active;
    }

    /** Markers in active zones, weighted toward the nearest player. Markers inside the minimum distance are left out. */
    public static List<Candidate> candidates(List<BlockPos> markers, ZoneIndex index, Set<ZoneId> active,
                                             Collection<BlockPos> players, GameSettings.Spawning s) {
        List<Candidate> candidates = new ArrayList<>();
        if (players.isEmpty()) {
            return candidates;
        }
        for (BlockPos marker : markers) {
            Optional<ZoneId> zone = index.zoneAt(marker);
            if (zone.isEmpty() || !active.contains(zone.get())) {
                continue;
            }
            double nearest = players.stream().mapToDouble(p -> Math.sqrt(p.distSqr(marker))).min().orElseThrow();
            if (nearest < s.minDistance()) {
                continue;
            }
            candidates.add(new Candidate(marker, 1.0 / (1.0 + nearest / s.falloffBlocks())));
        }
        return candidates;
    }

    public static Optional<BlockPos> pick(List<Candidate> candidates, RandomSource random) {
        double total = candidates.stream().mapToDouble(Candidate::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        double roll = random.nextDouble() * total;
        for (Candidate candidate : candidates) {
            roll -= candidate.weight();
            if (roll < 0) {
                return Optional.of(candidate.marker());
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1).marker());
    }
}
