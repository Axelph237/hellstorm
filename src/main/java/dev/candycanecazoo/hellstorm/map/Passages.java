package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds passages between zones: ways a walker two blocks tall, a player or a zombie, gets from one zone's walkable space
 * to another's by stepping sideways, jumping up one block, or falling. It crosses where the zones' boxes touch, or through
 * at most {@code maxGap} steps outside every zone, such as a doorway, an arch, or an open door. Open air alone joins
 * nothing: two zones drawn taller than the wall between them stay apart unless the wall is low enough to jump. A path
 * that needs a closed door is recorded separately: zombies can't open doors, so it is not a passage, but it is worth
 * telling the map maker about.
 */
public final class Passages {
    /** How far down a fall is followed. A player at full health survives a drop this far, so it still gets past a door. */
    static final int MAX_FALL = 16;

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

    /** One move to where a walker's feet end up, and the first closed door it passes through, or null. */
    private record Move(BlockPos to, BlockPos door) {}

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
        Map<BlockPos, ZoneId> owners = new HashMap<>();
        for (Zone zone : map.zones().values()) {
            Set<BlockPos> space = walkableSpace(zone, index, probe);
            spaces.put(zone.id(), space);
            space.forEach(spot -> owners.put(spot, zone.id()));
        }
        Map<ZoneId, Set<BlockPos>> openCells = new HashMap<>();
        for (Zone zone : map.zones().values()) {
            openCells.put(zone.id(), search(zone.id(), probe, maxGap, spaces.get(zone.id()), owners, open, closedDoors));
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
     * A zone's walkable space: the spots in it where a walker stands, in the largest group it can walk around in (steps up
     * or down one block), plus whatever it can fall to from there. Spots it can't get to, such as on top of a wall drawn
     * into the zone's boxes or in a gap inside a thick door, are left out, so a passage that only reaches them doesn't
     * connect the zone.
     */
    static Set<BlockPos> walkableSpace(Zone zone, ZoneIndex index, CellProbe probe) {
        Set<BlockPos> spots = new HashSet<>();
        for (BlockBox box : zone.boxes()) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    CellProbe.Cell below = probe.at(new BlockPos(x, box.minY() - 1, z));
                    CellProbe.Cell feet = probe.at(new BlockPos(x, box.minY(), z));
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        CellProbe.Cell head = probe.at(pos.above());
                        if (below == CellProbe.Cell.SOLID && passable(feet) && passable(head) && index.zoneAt(pos).filter(zone.id()::equals).isPresent()) {
                            spots.add(pos);
                        }
                        below = feet;
                        feet = head;
                    }
                }
            }
        }
        Set<BlockPos> space = new HashSet<>(largestPiece(spots));
        Deque<BlockPos> queue = new ArrayDeque<>(space);
        while (!queue.isEmpty()) {
            for (Move move : moves(probe, queue.poll())) {
                if (spots.contains(move.to) && space.add(move.to)) {
                    queue.add(move.to);
                }
            }
        }
        return space;
    }

    /** The largest group of spots joined by sideways steps up to one block up or down. */
    private static Set<BlockPos> largestPiece(Set<BlockPos> spots) {
        Set<BlockPos> largest = Set.of();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : spots) {
            if (!seen.add(start)) {
                continue;
            }
            Set<BlockPos> piece = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                piece.add(pos);
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = pos.relative(direction).above(dy);
                        if (spots.contains(next) && seen.add(next)) {
                            queue.add(next);
                        }
                    }
                }
            }
            if (piece.size() > largest.size()) {
                largest = piece;
            }
        }
        return largest;
    }

    /**
     * Walks out from one zone's walkable space, through spots in no zone's walkable space, recording every other zone it
     * reaches. Returns the open (not door) cells it passed through outside the zones.
     */
    private static Set<BlockPos> search(ZoneId zone, CellProbe probe, int maxGap, Set<BlockPos> own, Map<BlockPos, ZoneId> owners,
                                        Map<Link, BlockPos> open, Map<Link, BlockPos> closedDoors) {
        Set<BlockPos> reachedOpen = new HashSet<>();
        Deque<Step> queue = new ArrayDeque<>();
        own.forEach(spot -> queue.add(new Step(spot, 0, null)));
        Set<BlockPos> seenOpen = new HashSet<>(own);
        Set<BlockPos> seenViaDoor = new HashSet<>();
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            for (Move move : moves(probe, step.pos)) {
                BlockPos door = step.door != null ? step.door : move.door;
                ZoneId owner = owners.get(move.to);
                if (owner != null) {
                    if (!owner.equals(zone)) {
                        Link link = Link.of(zone, owner);
                        if (door == null) {
                            open.putIfAbsent(link, step.depth == 0 ? move.to : step.pos);
                        } else {
                            closedDoors.putIfAbsent(link, door);
                        }
                    }
                } else if (step.depth < maxGap && (door == null ? seenOpen.add(move.to) : !seenOpen.contains(move.to) && seenViaDoor.add(move.to))) {
                    if (door == null) {
                        reachedOpen.add(move.to);
                        reachedOpen.add(move.to.above());
                    }
                    queue.add(new Step(move.to, step.depth + 1, door));
                }
            }
        }
        return reachedOpen;
    }

    /**
     * Where a walker standing at {@code feet} can get to in one move: a step sideways, falling as far as {@link #MAX_FALL}
     * if there's no floor, or a jump up onto a block, which needs a third block of headroom. A wall two blocks tall, or a
     * fence or wall block of any height, stops it.
     */
    private static List<Move> moves(CellProbe probe, BlockPos feet) {
        List<Move> moves = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(direction);
            CellProbe.Cell sideCell = probe.at(side);
            if (passable(sideCell)) {
                CellProbe.Cell headCell = probe.at(side.above());
                if (!passable(headCell)) {
                    continue;
                }
                BlockPos door = headCell == CellProbe.Cell.CLOSED_DOOR ? side.above() : sideCell == CellProbe.Cell.CLOSED_DOOR ? side : null;
                BlockPos land = side;
                CellProbe.Cell under = probe.at(land.below());
                for (int fall = 0; passable(under) && fall < MAX_FALL; fall++) {
                    land = land.below();
                    if (door == null && under == CellProbe.Cell.CLOSED_DOOR) {
                        door = land;
                    }
                    under = probe.at(land.below());
                }
                if (under == CellProbe.Cell.SOLID) {
                    moves.add(new Move(land, door));
                }
            } else if (sideCell == CellProbe.Cell.SOLID) {
                BlockPos jump = feet.above(2);
                BlockPos landFeet = side.above();
                BlockPos landHead = side.above(2);
                CellProbe.Cell jumpCell = probe.at(jump);
                CellProbe.Cell feetCell = probe.at(landFeet);
                CellProbe.Cell headCell = probe.at(landHead);
                if (passable(jumpCell) && passable(feetCell) && passable(headCell)) {
                    BlockPos door = jumpCell == CellProbe.Cell.CLOSED_DOOR ? jump : headCell == CellProbe.Cell.CLOSED_DOOR ? landHead
                            : feetCell == CellProbe.Cell.CLOSED_DOOR ? landFeet : null;
                    moves.add(new Move(landFeet, door));
                }
            }
        }
        return moves;
    }

    private static boolean passable(CellProbe.Cell cell) {
        return cell == CellProbe.Cell.OPEN || cell == CellProbe.Cell.CLOSED_DOOR;
    }
}
