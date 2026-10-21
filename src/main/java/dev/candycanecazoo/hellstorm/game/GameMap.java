package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.SpawnLinks;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneGraph;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The map as one game sees it, fixed at game start. Without doors (phase 5), every zone reachable from a start zone is unlocked.
 */
public record GameMap(ZombiesMap map, ZoneIndex index, ZoneGraph graph, List<ZoneId> startZones, Set<ZoneId> unlocked,
                      Map<BlockPos, SpawnLinks.Link> spawnLinks) {
    public static GameMap of(ZombiesMap map, Set<Passages.Link> passages) {
        return of(map, passages, GameSettings.DEFAULT.windows().linkRange());
    }

    public static GameMap of(ZombiesMap map, Set<Passages.Link> passages, double windowLinkRange) {
        ZoneGraph graph = ZoneGraph.of(map, passages);
        List<ZoneId> starts = map.startZones().stream().map(Zone::id).toList();
        return new GameMap(map, ZoneIndex.of(map), graph, starts, Set.copyOf(graph.reachableFrom(starts)),
                Map.copyOf(SpawnLinks.resolve(map, windowLinkRange)));
    }

    public List<BlockPos> startZoneMarkers() {
        return spawnLinks.entrySet().stream().filter(e -> startZones.contains(e.getValue().zone()))
                .map(Map.Entry::getKey).sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).toList();
    }

    public Optional<WindowDef> windowFor(BlockPos marker) {
        return Optional.ofNullable(spawnLinks.get(marker)).flatMap(SpawnLinks.Link::window).map(map.windows()::get);
    }
}
