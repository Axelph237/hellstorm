package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Where a map is, and the same map somewhere else: for exporting it relative to its corner and importing it anywhere. */
public final class MapGeometry {
    private MapGeometry() {}

    /** The box around every zone, feature, window, and door block. Empty for a map with none. */
    public static Optional<BlockBox> bounds(ZombiesMap map) {
        BlockBox box = null;
        for (Zone zone : map.zones().values()) {
            for (BlockBox part : zone.boxes()) {
                box = box == null ? part : box.union(part);
            }
        }
        for (BlockPos pos : map.features().keySet()) {
            box = box == null ? BlockBox.single(pos) : box.union(BlockBox.single(pos));
        }
        for (WindowDef window : map.windows().values()) {
            box = box == null ? window.opening() : box.union(window.opening());
        }
        for (DoorDef door : map.doors().values()) {
            for (BlockPos pos : door.members()) {
                box = box == null ? BlockBox.single(pos) : box.union(BlockBox.single(pos));
            }
        }
        return Optional.ofNullable(box);
    }

    /** Every position in the map shifted by the same amount. */
    public static ZombiesMap moved(ZombiesMap map, int dx, int dy, int dz) {
        Map<ZoneId, Zone> zones = new TreeMap<>();
        map.zones().forEach((id, zone) -> zones.put(id, new Zone(id, zone.boxes().stream().map(b -> b.moved(dx, dy, dz)).toList(), zone.start())));
        Map<BlockPos, FeatureKind> features = new LinkedHashMap<>();
        map.features().forEach((pos, kind) -> features.put(pos.offset(dx, dy, dz), kind));
        Map<String, WindowDef> windows = new TreeMap<>();
        map.windows().forEach((id, w) -> windows.put(id, new WindowDef(id, w.opening().moved(dx, dy, dz), w.outward(), w.maxBoards())));
        Map<String, DoorDef> doors = new TreeMap<>();
        map.doors().forEach((id, d) -> doors.put(id, new DoorDef(id, d.members().stream().map(p -> p.offset(dx, dy, dz)).collect(Collectors.toSet()),
                d.cost(), d.requiresPower(), d.style())));
        return new ZombiesMap(zones, features, windows, doors);
    }
}
