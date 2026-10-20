package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the pathfinding inputs for a map: one grid per zone and one portal per connection. A zone's grid covers its
 * own cells plus the doorway cells it shares with neighbours, so a doorway node is walkable in both zones' grids and
 * becomes a zero-cost portal. Zones whose boxes touch get a portal between two adjacent walkable cells instead.
 */
public final class ZonePathing {
    private ZonePathing() {}

    public static Map<ZoneId, PassabilityGrid> grids(ZombiesMap map, Passages.Result passages, CellQuery world, MovementClass mc) {
        Map<ZoneId, Set<BlockPos>> doorways = doorwaysByZone(passages);
        Map<ZoneId, PassabilityGrid> grids = new HashMap<>();
        for (Zone zone : map.zones().values()) {
            if (zone.boxes().isEmpty()) {
                continue;
            }
            Set<BlockPos> doorway = doorways.getOrDefault(zone.id(), Set.of());
            CellQuery query = (x, y, z) -> zone.boxes().stream().anyMatch(b -> b.contains(x, y, z)) || doorway.contains(new BlockPos(x, y, z))
                    ? world.at(x, y, z) : CellQuery.Cell.SOLID;
            grids.put(zone.id(), PassabilityGrid.build(bounds(zone, doorway), query, mc));
        }
        return grids;
    }

    /**
     * One grid over every zone in {@code zones} plus the doorway cells between them, for flow fields that span the whole
     * map. Cells outside those zones and doorways read as solid.
     */
    public static PassabilityGrid unionGrid(ZombiesMap map, Set<ZoneId> zones, Passages.Result passages, CellQuery world, MovementClass mc) {
        Set<BlockPos> doorways = new HashSet<>();
        passages.gaps().forEach((link, cells) -> {
            if (zones.contains(link.a()) && zones.contains(link.b())) {
                doorways.addAll(cells);
            }
        });
        List<BlockBox> boxes = map.zones().values().stream().filter(z -> zones.contains(z.id())).flatMap(z -> z.boxes().stream()).toList();
        if (boxes.isEmpty()) {
            return PassabilityGrid.build(new BlockBox(0, 0, 0, 0, 0, 0), (x, y, z) -> CellQuery.Cell.SOLID, mc);
        }
        CellQuery query = (x, y, z) -> boxes.stream().anyMatch(b -> b.contains(x, y, z)) || doorways.contains(new BlockPos(x, y, z))
                ? world.at(x, y, z) : CellQuery.Cell.SOLID;
        Zone union = new Zone(new ZoneId("union"), boxes, false);
        return PassabilityGrid.build(bounds(union, doorways), query, mc);
    }

    public static List<Portal> portals(Passages.Result passages, Map<ZoneId, PassabilityGrid> grids) {
        List<Portal> portals = new ArrayList<>();
        List<Passages.Link> links = new ArrayList<>(passages.open());
        links.sort(Comparator.comparing((Passages.Link l) -> l.a()).thenComparing(Passages.Link::b));
        for (Passages.Link link : links) {
            PassabilityGrid a = grids.get(link.a());
            PassabilityGrid b = grids.get(link.b());
            if (a == null || b == null) {
                continue;
            }
            String id = link.a() + "|" + link.b();
            Set<BlockPos> gap = passages.gap(link);
            // The shared gap can also hold open air above the rooms or around the ends of a wall. A real doorway cell is
            // walkable in both grids and steps sideways onto a walkable cell of each zone itself.
            Optional<BlockPos> doorway = gap.stream()
                    .filter(p -> a.walkable(p) && b.walkable(p) && stepsInto(a, p, gap) && stepsInto(b, p, gap))
                    .min(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingLong(BlockPos::asLong));
            if (doorway.isPresent()) {
                portals.add(new Portal(id, link.a(), doorway.get(), link.b(), doorway.get(), 0));
            } else {
                adjacentPair(a, b).ifPresent(pair -> portals.add(new Portal(id, link.a(), pair[0], link.b(), pair[1], 1)));
            }
        }
        return portals;
    }

    private static boolean stepsInto(PassabilityGrid grid, BlockPos cell, Set<BlockPos> gap) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos next = cell.relative(d);
            if (!gap.contains(next) && grid.walkable(next)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<BlockPos[]> adjacentPair(PassabilityGrid a, PassabilityGrid b) {
        BlockBox box = a.bounds();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (!a.walkable(x, y, z)) {
                        continue;
                    }
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos next = new BlockPos(x + d.getStepX(), y, z + d.getStepZ());
                        if (b.walkable(next)) {
                            return Optional.of(new BlockPos[] {new BlockPos(x, y, z), next});
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Map<ZoneId, Set<BlockPos>> doorwaysByZone(Passages.Result passages) {
        Map<ZoneId, Set<BlockPos>> byZone = new HashMap<>();
        passages.gaps().forEach((link, cells) -> {
            byZone.computeIfAbsent(link.a(), k -> new HashSet<>()).addAll(cells);
            byZone.computeIfAbsent(link.b(), k -> new HashSet<>()).addAll(cells);
        });
        return byZone;
    }

    private static BlockBox bounds(Zone zone, Set<BlockPos> doorway) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockBox b : zone.boxes()) {
            minX = Math.min(minX, b.minX()); minY = Math.min(minY, b.minY()); minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxX()); maxY = Math.max(maxY, b.maxY()); maxZ = Math.max(maxZ, b.maxZ());
        }
        for (BlockPos p : doorway) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
