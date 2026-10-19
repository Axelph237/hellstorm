package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneGraph;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
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
    private static final GameSettings.Spawning S = new GameSettings.Spawning(1, 24.0, 6.0);

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

    @Test
    void activeZonesAreThePlayersZoneAndItsNeighbours() {
        ZoneGraph graph = ZoneGraph.of(chain(), Set.of());
        assertEquals(Set.of(A, B, C), SpawnDirector.activeZones(List.of(B), graph, Set.copyOf(ZONES), 1));
        assertEquals(Set.of(B), SpawnDirector.activeZones(List.of(B), graph, Set.copyOf(ZONES), 0));
        assertEquals(Set.copyOf(ZONES), SpawnDirector.activeZones(List.of(B), graph, Set.copyOf(ZONES), 2));
    }

    @Test
    void lockedZonesAreNeverActiveAndBlockTheWayThrough() {
        ZoneGraph graph = ZoneGraph.of(chain(), Set.of());
        assertEquals(Set.of(A), SpawnDirector.activeZones(List.of(A), graph, Set.of(A, C, D), 3), "B is locked, so C and D are cut off");
        assertEquals(Set.of(), SpawnDirector.activeZones(List.of(B), graph, Set.of(A), 1), "a player in a locked zone activates nothing");
    }

    @Test
    void candidatesComeOnlyFromActiveZonesOutsideTheMinimumDistance() {
        ZombiesMap map = chain();
        BlockPos player = new BlockPos(13, 0, 5);
        List<SpawnDirector.Candidate> candidates = SpawnDirector.candidates(MARKERS, ZoneIndex.of(map), Set.of(A, B, C), List.of(player), S);
        assertEquals(List.of(marker(0), marker(2)), candidates.stream().map(SpawnDirector.Candidate::marker).toList(),
                "B's marker is 2 blocks from the player (under 6), D is not active");
        assertTrue(candidates.get(0).weight() > candidates.get(1).weight(), "closer marker weighs more");
    }

    @Test
    void markersOutsideEveryZoneAreNotCandidates() {
        List<SpawnDirector.Candidate> candidates = SpawnDirector.candidates(List.of(new BlockPos(500, 0, 500)),
                ZoneIndex.of(chain()), Set.copyOf(ZONES), List.of(new BlockPos(1, 0, 1)), S);
        assertEquals(List.of(), candidates);
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
