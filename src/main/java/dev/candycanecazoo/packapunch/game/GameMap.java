package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneGraph;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

/**
 * The map as one game sees it, fixed at game start. Without doors (phase 5), every zone reachable from a start zone is unlocked.
 */
public record GameMap(ZombiesMap map, ZoneIndex index, ZoneGraph graph, List<ZoneId> startZones, Set<ZoneId> unlocked, List<BlockPos> spawnMarkers) {
    public static GameMap of(ZombiesMap map, Set<Passages.Link> passages) {
        ZoneGraph graph = ZoneGraph.of(map, passages);
        List<ZoneId> starts = map.startZones().stream().map(Zone::id).toList();
        return new GameMap(map, ZoneIndex.of(map), graph, starts, Set.copyOf(graph.reachableFrom(starts)), map.features(FeatureKind.SPAWN));
    }

    public List<BlockPos> startZoneMarkers() {
        return spawnMarkers.stream().filter(m -> index.zoneAt(m).map(startZones::contains).orElse(false)).toList();
    }
}
