package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Chooses where the next zombie spawns: a marker feeding a zone a player has discovered, weighted toward the players. */
public final class SpawnDirector {
    public record Candidate(BlockPos marker, double weight) {}

    private SpawnDirector() {}

    /**
     * Markers that feed a discovered zone, weighted toward the nearest player. Markers inside the minimum distance are left
     * out, unless every one of them is that close: a round never stalls because the players stand on the only markers.
     * With no player positions known, every discovered marker weighs the same.
     * {@code markerZones} says which zone each marker feeds (see {@link dev.candycanecazoo.hellstorm.map.SpawnLinks}).
     */
    public static List<Candidate> candidates(Map<BlockPos, ZoneId> markerZones, Set<ZoneId> discovered,
                                             Collection<BlockPos> players, GameSettings.Spawning s) {
        List<Candidate> far = new ArrayList<>();
        List<Candidate> near = new ArrayList<>();
        markerZones.keySet().stream().filter(marker -> discovered.contains(markerZones.get(marker)))
                .sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(marker -> {
                    double nearest = players.stream().mapToDouble(p -> Math.sqrt(p.distSqr(marker))).min().orElse(Double.POSITIVE_INFINITY);
                    double weight = Double.isInfinite(nearest) ? 1.0 : 1.0 / (1.0 + nearest / s.falloffBlocks());
                    (nearest < s.minDistance() ? near : far).add(new Candidate(marker, weight));
                });
        return far.isEmpty() ? near : far;
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
