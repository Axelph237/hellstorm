package dev.candycanecazoo.hellstorm.map;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Answers "which zone is this position in?" by bucketing zone boxes into the 16³ chunk sections they overlap. */
public final class ZoneIndex {
    private record Entry(ZoneId zone, BlockBox box) {}

    private final Long2ObjectOpenHashMap<List<Entry>> bySection = new Long2ObjectOpenHashMap<>();

    public ZoneIndex(Collection<Zone> zones) {
        for (Zone zone : zones) {
            for (BlockBox box : zone.boxes()) {
                Entry entry = new Entry(zone.id(), box);
                for (int sx = SectionPos.blockToSectionCoord(box.minX()); sx <= SectionPos.blockToSectionCoord(box.maxX()); sx++) {
                    for (int sy = SectionPos.blockToSectionCoord(box.minY()); sy <= SectionPos.blockToSectionCoord(box.maxY()); sy++) {
                        for (int sz = SectionPos.blockToSectionCoord(box.minZ()); sz <= SectionPos.blockToSectionCoord(box.maxZ()); sz++) {
                            bySection.computeIfAbsent(SectionPos.asLong(sx, sy, sz), k -> new ArrayList<>()).add(entry);
                        }
                    }
                }
            }
        }
    }

    public static ZoneIndex of(ZombiesMap map) {
        return new ZoneIndex(map.zones().values());
    }

    public Optional<ZoneId> zoneAt(int x, int y, int z) {
        List<Entry> entries = bySection.get(SectionPos.asLong(
                SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z)));
        if (entries == null) {
            return Optional.empty();
        }
        for (Entry entry : entries) {
            if (entry.box.contains(x, y, z)) {
                return Optional.of(entry.zone);
            }
        }
        return Optional.empty();
    }

    public Optional<ZoneId> zoneAt(BlockPos pos) {
        return zoneAt(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * The zone someone whose feet are in {@code feet} stands in. Standing on a carpet, slab or stair puts the feet in that
     * block, which a zone drawn over the floor leaves out, so the block above counts too.
     */
    public Optional<ZoneId> zoneOfBody(BlockPos feet) {
        return zoneAt(feet).or(() -> zoneAt(feet.getX(), feet.getY() + 1, feet.getZ()));
    }
}
