package dev.candycanecazoo.packapunch.map;

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
public record ZombiesMap(Map<ZoneId, Zone> zones, Map<BlockPos, FeatureKind> features) {
    public static final ZombiesMap EMPTY = new ZombiesMap(Map.of(), Map.of());

    public static final Codec<ZombiesMap> CODEC = RecordCodecBuilder.create(i -> i.group(
            Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(m -> List.copyOf(m.zones.values())),
            FeatureRef.CODEC.listOf().optionalFieldOf("features", List.of()).forGetter(ZombiesMap::featureList)
    ).apply(i, ZombiesMap::fromLists));

    public ZombiesMap {
        zones = new TreeMap<>(zones);
        features = sortedFeatures(features);
    }

    private static ZombiesMap fromLists(List<Zone> zones, List<FeatureRef> features) {
        Map<ZoneId, Zone> zoneMap = new TreeMap<>();
        zones.forEach(z -> zoneMap.put(z.id(), z));
        Map<BlockPos, FeatureKind> featureMap = new LinkedHashMap<>();
        features.forEach(f -> featureMap.put(f.pos(), f.kind()));
        return new ZombiesMap(zoneMap, featureMap);
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
        return new ZombiesMap(next, features);
    }

    public ZombiesMap withoutZone(ZoneId id) {
        Map<ZoneId, Zone> next = new TreeMap<>(zones);
        next.remove(id);
        return new ZombiesMap(next, features);
    }

    public ZombiesMap withFeature(BlockPos pos, FeatureKind kind) {
        if (features.get(pos) == kind) {
            return this;
        }
        Map<BlockPos, FeatureKind> next = new LinkedHashMap<>(features);
        next.put(pos.immutable(), kind);
        return new ZombiesMap(zones, next);
    }

    public ZombiesMap withoutFeature(BlockPos pos) {
        if (!features.containsKey(pos)) {
            return this;
        }
        Map<BlockPos, FeatureKind> next = new LinkedHashMap<>(features);
        next.remove(pos);
        return new ZombiesMap(zones, next);
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
