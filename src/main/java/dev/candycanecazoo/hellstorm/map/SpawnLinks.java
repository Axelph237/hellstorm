package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which zone each spawn marker feeds. A marker inside a zone spawns zombies straight into it. A marker outside every zone
 * links to the nearest window it stands in front of, within range, and its zombies break in through that window into the
 * zone behind it.
 */
public final class SpawnLinks {
    public record Link(ZoneId zone, Optional<String> window) {}

    private SpawnLinks() {}

    public static Map<BlockPos, Link> resolve(ZombiesMap map, double linkRange) {
        ZoneIndex index = ZoneIndex.of(map);
        Map<BlockPos, Link> links = new LinkedHashMap<>();
        for (BlockPos marker : map.features(FeatureKind.SPAWN)) {
            Optional<ZoneId> zone = index.zoneAt(marker);
            if (zone.isPresent()) {
                links.put(marker, new Link(zone.get(), Optional.empty()));
                continue;
            }
            nearestWindowFacing(map, marker, linkRange).ifPresent(w ->
                    w.zoneBehind(index).ifPresent(behind -> links.put(marker, new Link(behind, Optional.of(w.id())))));
        }
        return links;
    }

    static Optional<WindowDef> nearestWindowFacing(ZombiesMap map, BlockPos marker, double range) {
        return map.windows().values().stream()
                .filter(w -> inFront(w, marker))
                .filter(w -> w.attackSpot().distSqr(marker) <= range * range)
                .min(Comparator.comparingDouble((WindowDef w) -> w.attackSpot().distSqr(marker)).thenComparing(WindowDef::id));
    }

    /** True when the marker is on the window's outward side of the wall. */
    static boolean inFront(WindowDef window, BlockPos marker) {
        Direction out = window.outward();
        BlockPos face = window.bottomCenter();
        int along = (marker.getX() - face.getX()) * out.getStepX() + (marker.getZ() - face.getZ()) * out.getStepZ();
        return along > 0;
    }
}
