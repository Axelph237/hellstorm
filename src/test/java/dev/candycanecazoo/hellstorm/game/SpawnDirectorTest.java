package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.SpawnLinks;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import net.minecraft.core.Direction;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Four 10-wide zones in a row, A-B-C-D along x, each touching the next. One marker in the middle of each. */
class SpawnDirectorTest {
    private static final ZoneId A = new ZoneId("a"), B = new ZoneId("b"), C = new ZoneId("c"), D = new ZoneId("d");
    private static final List<ZoneId> ZONES = List.of(A, B, C, D);
    private static final GameSettings.Spawning S = new GameSettings.Spawning(24.0, 6.0);

    private static ZombiesMap chain() {
        ZombiesMap map = ZombiesMap.EMPTY;
        for (int i = 0; i < ZONES.size(); i++) {
            map = map.withZone(new Zone(ZONES.get(i), List.of(new BlockBox(i * 10, 0, 0, i * 10 + 9, 3, 9)), i == 0));
        }
        return map;
    }

    private static BlockPos marker(int zone) {
        return new BlockPos(zone * 10 + 5, 0, 5);
    }

    private static final List<BlockPos> MARKERS = List.of(marker(0), marker(1), marker(2), marker(3));

    private static Map<BlockPos, ZoneId> markerZones(ZombiesMap map) {
        Map<BlockPos, ZoneId> zones = new HashMap<>();
        SpawnLinks.resolve(map, 6.0).forEach((marker, link) -> zones.put(marker, link.zone()));
        return zones;
    }

    private static ZombiesMap chainWithMarkers() {
        ZombiesMap map = chain();
        for (BlockPos marker : MARKERS) {
            map = map.withFeature(marker, FeatureKind.SPAWN);
        }
        return map;
    }

    private static List<BlockPos> markersOf(List<SpawnDirector.Candidate> candidates) {
        return candidates.stream().map(SpawnDirector.Candidate::marker).toList();
    }

    @Test
    void candidatesComeOnlyFromDiscoveredZonesOutsideTheMinimumDistance() {
        BlockPos player = new BlockPos(13, 0, 5);
        List<SpawnDirector.Candidate> candidates = SpawnDirector.candidates(markerZones(chainWithMarkers()), Set.of(A, B, D), List.of(player), S);
        assertEquals(List.of(marker(0), marker(3)), markersOf(candidates),
                "B's marker is 2 blocks from the player (under 6), and nobody has discovered C");
        assertTrue(candidates.get(0).weight() > candidates.get(1).weight(), "closer marker weighs more");
    }

    @Test
    void aDiscoveredZoneFarFromThePlayersStillSpawns() {
        BlockPos player = new BlockPos(5, 0, 2);
        assertEquals(List.of(marker(3)), markersOf(SpawnDirector.candidates(markerZones(chainWithMarkers()), Set.of(D), List.of(player), S)),
                "D is three zones away and not next to the player, but discovered");
    }

    @Test
    void whenEveryDiscoveredMarkerIsTooCloseTheyAllStayCandidates() {
        BlockPos player = new BlockPos(13, 0, 5);
        assertEquals(List.of(marker(1)), markersOf(SpawnDirector.candidates(markerZones(chainWithMarkers()), Set.of(B), List.of(player), S)),
                "the round would stall otherwise");
    }

    @Test
    void withNoPlayerPositionEveryDiscoveredMarkerWeighsTheSame() {
        List<SpawnDirector.Candidate> candidates = SpawnDirector.candidates(markerZones(chainWithMarkers()), Set.of(A, C), List.of(), S);
        assertEquals(List.of(new SpawnDirector.Candidate(marker(0), 1.0), new SpawnDirector.Candidate(marker(2), 1.0)), candidates);
    }

    @Test
    void unlinkedMarkersAreNotCandidatesButWindowMarkersFeedTheWindowsZone() {
        ZombiesMap map = chain()
                .withWindow(new WindowDef("w", new BlockBox(5, 0, -1, 5, 1, -1), Direction.NORTH, 6, A))
                .withFeature(new BlockPos(5, 0, -3), FeatureKind.SPAWN)
                .withFeature(new BlockPos(500, 0, 500), FeatureKind.SPAWN);
        List<SpawnDirector.Candidate> candidates = SpawnDirector.candidates(markerZones(map), Set.of(A), List.of(new BlockPos(1, 0, 8)), S);
        assertEquals(List.of(new BlockPos(5, 0, -3)), candidates.stream().map(SpawnDirector.Candidate::marker).toList());
    }

    @Test
    void picksFollowTheWeights() {
        List<SpawnDirector.Candidate> candidates = List.of(
                new SpawnDirector.Candidate(marker(0), 3.0), new SpawnDirector.Candidate(marker(1), 1.0));
        RandomSource random = RandomSource.create(42);
        Map<BlockPos, Integer> counts = new HashMap<>();
        for (int i = 0; i < 20_000; i++) {
            counts.merge(SpawnDirector.pick(candidates, random).orElseThrow(), 1, Integer::sum);
        }
        assertEquals(0.75, counts.get(marker(0)) / 20_000.0, 0.02);
        assertTrue(SpawnDirector.pick(List.of(), random).isEmpty());
    }
}
