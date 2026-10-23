package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds passages between zones: where their boxes touch and a mob can step across, and through the cells that belong to
 * no zone, such as a doorway, an arch, or an open door. Searches at most {@code maxGap} cells out from each zone's surface. A path that needs a closed door is recorded
 * separately: zombies can't open doors, so it is not a passage, but it is worth telling the map maker about.
 */
public final class Passages {
    /** An unordered pair of zones. */
    public record Link(ZoneId a, ZoneId b) {
        public static Link of(ZoneId x, ZoneId y) {
            return x.compareTo(y) <= 0 ? new Link(x, y) : new Link(y, x);
        }
    }

    /**
     * {@code openings} holds, for each open link, the first cell found where a mob crosses into the other zone.
     * {@code gaps} holds, for each open link, the open cells outside every zone that both zones' searches reached: the
     * doorway itself. Zones joined only where their boxes touch have an empty gap.
     */
    public record Result(Map<Link, BlockPos> openings, Map<Link, BlockPos> closedDoors, Map<Link, Set<BlockPos>> gaps) {
        public Set<Link> open() {
            return openings.keySet();
        }

        public Set<BlockPos> gap(Link link) {
            return gaps.getOrDefault(link, Set.of());
        }
    }

    private record Step(BlockPos pos, int depth, BlockPos door) {}

    private Passages() {}

    /** Passages with every door players buy still shut, as before a game and while editing. */
    public static Result find(ZombiesMap map, ZoneIndex index, CellProbe world, int maxGap) {
        return find(map, index, world, maxGap, Set.of());
    }

    /**
     * Passages with only the doors in {@code openDoors} bought. A shut door's blocks are a wall whatever they're made of:
     * the world reads a wooden door, a cobweb or a carpet as open, but nothing gets through until the door is bought.
     */
    public static Result find(ZombiesMap map, ZoneIndex index, CellProbe world, int maxGap, Set<String> openDoors) {
        Set<BlockPos> shut = new HashSet<>();
        map.doors().values().stream().filter(d -> !openDoors.contains(d.id())).forEach(d -> shut.addAll(d.members()));
        CellProbe probe = pos -> shut.contains(pos) ? CellProbe.Cell.SOLID : world.at(pos);
        Map<Link, BlockPos> open = new LinkedHashMap<>();
        Map<Link, BlockPos> closedDoors = new LinkedHashMap<>();
        Map<ZoneId, Set<BlockPos>> spaces = new HashMap<>();
        for (Zone zone : map.zones().values()) {
            spaces.put(zone.id(), walkableSpace(zone, index, probe));
        }
        Map<ZoneId, Set<BlockPos>> openCells = new HashMap<>();
        for (Zone zone : map.zones().values()) {
            openCells.put(zone.id(), search(zone, index, probe, maxGap, spaces, open, closedDoors));
        }
        closedDoors.keySet().removeAll(open.keySet());
        Map<Link, Set<BlockPos>> gaps = new HashMap<>();
        for (Link link : open.keySet()) {
            Set<BlockPos> shared = new HashSet<>(openCells.getOrDefault(link.a(), Set.of()));
            shared.retainAll(openCells.getOrDefault(link.b(), Set.of()));
            gaps.put(link, Set.copyOf(shared));
        }
        return new Result(open, closedDoors, gaps);
    }

    /**
     * A zone's walkable space: the largest connected group of its cells that aren't solid. Air sealed off from the rest
     * of the zone, such as the gaps inside a thick door drawn into the zone's boxes, is left out, so a passage that only
     * reaches such a pocket doesn't connect the zone.
     */
    static Set<BlockPos> walkableSpace(Zone zone, ZoneIndex index, CellProbe probe) {
        Set<BlockPos> cells = new HashSet<>();
        for (BlockBox box : zone.boxes()) {
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (index.zoneAt(pos).filter(zone.id()::equals).isPresent() && probe.at(pos) != CellProbe.Cell.SOLID) {
                    cells.add(pos.immutable());
                }
            }
        }
        Set<BlockPos> largest = Set.of();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : cells) {
            if (!seen.add(start)) {
                continue;
            }
            Set<BlockPos> group = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                group.add(pos);
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (cells.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (group.size() > largest.size()) {
                largest = group;
            }
        }
        return largest;
    }

    /** Searches out from one zone's walkable space. Returns the open (not door) cells it reached. */
    private static Set<BlockPos> search(Zone zone, ZoneIndex index, CellProbe probe, int maxGap, Map<ZoneId, Set<BlockPos>> spaces,
                                        Map<Link, BlockPos> open, Map<Link, BlockPos> closedDoors) {
        Set<BlockPos> own = spaces.get(zone.id());
        Set<BlockPos> reachedOpen = new HashSet<>();
        Deque<Step> queue = new ArrayDeque<>();
        Set<BlockPos> seenOpen = new HashSet<>();
        Set<BlockPos> seenViaDoor = new HashSet<>();
        for (BlockBox box : zone.boxes()) {
            forEachShellCell(box, (inside, cell) -> {
                if (!own.contains(inside)) {
                    return;
                }
                Optional<ZoneId> owner = index.zoneAt(cell);
                if (owner.isEmpty()) {
                    queue.add(new Step(cell, 1, null));
                } else if (!owner.get().equals(zone.id()) && spaces.get(owner.get()).contains(cell)) {
                    crossing(probe, inside, cell).ifPresent(door -> {
                        Link link = Link.of(zone.id(), owner.get());
                        if (door.isEmpty()) {
                            open.putIfAbsent(link, cell);
                        } else {
                            closedDoors.putIfAbsent(link, door.get());
                        }
                    });
                }
            });
        }
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            Set<BlockPos> seen = step.door == null ? seenOpen : seenViaDoor;
            if (seenOpen.contains(step.pos) || !seen.add(step.pos)) {
                continue;
            }
            CellProbe.Cell cell = probe.at(step.pos);
            if (cell == CellProbe.Cell.SOLID) {
                continue;
            }
            if (cell == CellProbe.Cell.OPEN && step.door == null) {
                reachedOpen.add(step.pos);
            }
            BlockPos door = cell == CellProbe.Cell.CLOSED_DOOR && step.door == null ? step.pos : step.door;
            for (Direction direction : Direction.values()) {
                BlockPos next = step.pos.relative(direction);
                Optional<ZoneId> owner = index.zoneAt(next);
                if (owner.isPresent()) {
                    if (!owner.get().equals(zone.id()) && spaces.get(owner.get()).contains(next)) {
                        Link link = Link.of(zone.id(), owner.get());
                        if (door == null) {
                            open.putIfAbsent(link, step.pos);
                        } else {
                            closedDoors.putIfAbsent(link, door);
                        }
                    }
                } else if (step.depth < maxGap) {
                    queue.add(new Step(next, step.depth + 1, door));
                }
            }
        }
        return reachedOpen;
    }

    /**
     * Whether a mob can step from {@code inside} one zone to {@code outside} in the zone next to it, where their boxes touch.
     * Empty when either cell is solid, such as a wall or a bought door's blocks drawn inside the boxes. Otherwise holds
     * the closed door in the way, if there is one.
     */
    private static Optional<Optional<BlockPos>> crossing(CellProbe probe, BlockPos inside, BlockPos outside) {
        CellProbe.Cell from = probe.at(inside);
        CellProbe.Cell to = probe.at(outside);
        if (from == CellProbe.Cell.SOLID || to == CellProbe.Cell.SOLID) {
            return Optional.empty();
        }
        if (from == CellProbe.Cell.CLOSED_DOOR) {
            return Optional.of(Optional.of(inside));
        }
        return Optional.of(to == CellProbe.Cell.CLOSED_DOOR ? Optional.of(outside) : Optional.empty());
    }

    /** Every cell that shares a face with the box but lies outside it, with the cell inside the box across that face. */
    private static void forEachShellCell(BlockBox box, java.util.function.BiConsumer<BlockPos, BlockPos> action) {
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                action.accept(new BlockPos(box.minX(), y, z), new BlockPos(box.minX() - 1, y, z));
                action.accept(new BlockPos(box.maxX(), y, z), new BlockPos(box.maxX() + 1, y, z));
            }
        }
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                action.accept(new BlockPos(x, box.minY(), z), new BlockPos(x, box.minY() - 1, z));
                action.accept(new BlockPos(x, box.maxY(), z), new BlockPos(x, box.maxY() + 1, z));
            }
        }
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                action.accept(new BlockPos(x, y, box.minZ()), new BlockPos(x, y, box.minZ() - 1));
                action.accept(new BlockPos(x, y, box.maxZ()), new BlockPos(x, y, box.maxZ() + 1));
            }
        }
    }
}
