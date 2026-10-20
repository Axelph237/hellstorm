package dev.candycanecazoo.packapunch.map;

import dev.candycanecazoo.packapunch.util.Codecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The authored shape of a zombies map: its zones and the positions of its feature blocks.
 * Immutable: every edit returns a new map. The same codec is used for saved data, network sync, and export.
 */
public record ZombiesMap(Map<ZoneId, Zone> zones, Map<BlockPos, FeatureKind> features, Map<String, WindowDef> windows,
                         Map<String, DoorDef> doors) {
    public static final ZombiesMap EMPTY = new ZombiesMap(Map.of(), Map.of(), Map.of(), Map.of());

    public static final Codec<ZombiesMap> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.strictOptional(Zone.CODEC.listOf(), "zones", List.of()).forGetter(m -> List.copyOf(m.zones.values())),
            Codecs.strictOptional(FeatureRef.CODEC.listOf(), "features", List.of()).forGetter(ZombiesMap::featureList),
            Codecs.strictOptional(WindowDef.CODEC.listOf(), "windows", List.of()).forGetter(m -> List.copyOf(m.windows.values())),
            Codecs.strictOptional(DoorDef.CODEC.listOf(), "doors", List.of()).forGetter(m -> List.copyOf(m.doors.values()))
    ).apply(i, ZombiesMap::fromLists));

    public ZombiesMap {
        zones = new TreeMap<>(zones);
        features = sortedFeatures(features);
        windows = new TreeMap<>(windows);
        doors = new TreeMap<>(doors);
    }

    private static ZombiesMap fromLists(List<Zone> zones, List<FeatureRef> features, List<WindowDef> windows, List<DoorDef> doors) {
        Map<ZoneId, Zone> zoneMap = new TreeMap<>();
        zones.forEach(z -> zoneMap.put(z.id(), z));
        Map<BlockPos, FeatureKind> featureMap = new LinkedHashMap<>();
        features.forEach(f -> featureMap.put(f.pos(), f.kind()));
        Map<String, WindowDef> windowMap = new TreeMap<>();
        windows.forEach(w -> windowMap.put(w.id(), w));
        Map<String, DoorDef> doorMap = new TreeMap<>();
        doors.forEach(d -> doorMap.put(d.id(), d));
        return new ZombiesMap(zoneMap, featureMap, windowMap, doorMap);
    }

    public ZombiesMap withWindow(WindowDef window) {
        Map<String, WindowDef> next = new TreeMap<>(windows);
        next.put(window.id(), window);
        return new ZombiesMap(zones, features, next, doors);
    }

    public ZombiesMap withoutWindow(String id) {
        Map<String, WindowDef> next = new TreeMap<>(windows);
        next.remove(id);
        return new ZombiesMap(zones, features, next, doors);
    }

    public ZombiesMap withDoor(DoorDef door) {
        Map<String, DoorDef> next = new TreeMap<>(doors);
        next.put(door.id(), door);
        return new ZombiesMap(zones, features, windows, next);
    }

    public ZombiesMap withoutDoor(String id) {
        Map<String, DoorDef> next = new TreeMap<>(doors);
        next.remove(id);
        return new ZombiesMap(zones, features, windows, next);
    }

    public Optional<DoorDef> doorAt(BlockPos pos) {
        return doors.values().stream().filter(d -> d.members().contains(pos)).findFirst();
    }

    public Optional<WindowDef> windowAt(BlockPos pos) {
        return windows.values().stream().filter(w -> w.opening().contains(pos)).findFirst();
    }

    private static Map<BlockPos, FeatureKind> sortedFeatures(Map<BlockPos, FeatureKind> features) {
        Map<BlockPos, FeatureKind> sorted = new TreeMap<>(Comparator.comparingLong(BlockPos::asLong));
        sorted.putAll(features);
        return sorted;
    }

    public List<FeatureRef> featureList() {
        return features.entrySet().stream().map(e -> new FeatureRef(e.getKey(), e.getValue())).toList();
    }

    public List<BlockPos> features(FeatureKind kind) {
        return features.entrySet().stream().filter(e -> e.getValue() == kind).map(Map.Entry::getKey).toList();
    }

    public Optional<Zone> zone(ZoneId id) {
        return Optional.ofNullable(zones.get(id));
    }

    public Optional<Zone> zoneAt(BlockPos pos) {
        return zones.values().stream().filter(z -> z.contains(pos)).findFirst();
    }

    /** The first zone other than {@code ignore} that has a box intersecting {@code box}. */
    public Optional<ZoneId> zoneOverlapping(BlockBox box, ZoneId ignore) {
        return zones.values().stream()
                .filter(z -> !z.id().equals(ignore))
                .filter(z -> z.boxes().stream().anyMatch(b -> b.intersects(box)))
                .map(Zone::id)
                .findFirst();
    }

    public ZombiesMap withZone(Zone zone) {
        Map<ZoneId, Zone> next = new TreeMap<>(zones);
        next.put(zone.id(), zone);
        return new ZombiesMap(next, features, windows, doors);
    }

    public ZombiesMap withoutZone(ZoneId id) {
        Map<ZoneId, Zone> next = new TreeMap<>(zones);
        next.remove(id);
        return new ZombiesMap(next, features, windows, doors);
    }

    public ZombiesMap withFeature(BlockPos pos, FeatureKind kind) {
        if (features.get(pos) == kind) {
            return this;
        }
        Map<BlockPos, FeatureKind> next = new LinkedHashMap<>(features);
        next.put(pos.immutable(), kind);
        return new ZombiesMap(zones, next, windows, doors);
    }

    public ZombiesMap withoutFeature(BlockPos pos) {
        if (!features.containsKey(pos)) {
            return this;
        }
        Map<BlockPos, FeatureKind> next = new LinkedHashMap<>(features);
        next.remove(pos);
        return new ZombiesMap(zones, next, windows, doors);
    }

    public List<Zone> startZones() {
        List<Zone> starts = new ArrayList<>();
        zones.values().forEach(z -> {
            if (z.start()) {
                starts.add(z);
            }
        });
        return starts;
    }
}
