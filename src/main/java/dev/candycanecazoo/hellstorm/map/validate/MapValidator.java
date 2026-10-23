package dev.candycanecazoo.hellstorm.map.validate;

import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.CellProbe;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.SpawnLinks;
import dev.candycanecazoo.hellstorm.map.StandingArea;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneGraph;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.util.Highlight;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure checks that a map can be played. Each check has a stable problem code so tests and tools can match on it.
 * Only problems at a real location carry a position. Zone-level problems name the zone instead of pointing at an arbitrary block.
 */
public final class MapValidator {
    public static final String NO_ZONES = "no_zones";
    public static final String EMPTY_ZONE = "empty_zone";
    public static final String NO_START_ZONE = "no_start_zone";
    public static final String ZONE_OVERLAP = "zone_overlap";
    public static final String UNREACHABLE_ZONE = "unreachable_zone";
    public static final String ZONE_WITHOUT_SPAWN = "zone_without_spawn";
    public static final String SPAWN_OUTSIDE_ZONE = "spawn_outside_zone";
    public static final String CLOSED_DOOR_GAP = "closed_door_gap";
    public static final String WINDOW_TOO_LARGE = "window_too_large";
    public static final String WINDOW_UNKNOWN_ZONE = "window_unknown_zone";
    public static final String WINDOW_WITHOUT_SPAWN = "window_without_spawn";
    public static final String MOB_TOO_BIG_FOR_WINDOW = "mob_too_big_for_window";
    public static final String SPLIT_START_ZONE = "split_start_zone";
    public static final String EMPTY_DOOR = "empty_door";
    public static final String DOOR_NOT_BETWEEN_ZONES = "door_not_between_zones";
    /** A door's rooms are joined anyway, through an opening that isn't a door, so the door unlocks nothing. */
    public static final String LOOPHOLE = "loophole";

    /** Passages are searched this many cells out from each zone unless the caller says otherwise. */
    public static final int DEFAULT_MAX_GAP = 2;

    private MapValidator() {}

    /** Validates without looking at the world: only zones whose boxes touch are connected. */
    public static ValidationReport validate(ZombiesMap map) {
        return validate(map, CellProbe.zonesOnly(ZoneIndex.of(map)), DEFAULT_MAX_GAP);
    }

    public static ValidationReport validate(ZombiesMap map, CellProbe probe, int maxGap) {
        return validate(map, new ValidationContext(probe, ValidationContext.OFFLINE.standable(), maxGap, ValidationContext.OFFLINE.windows(), List.of()));
    }

    public static ValidationReport validate(ZombiesMap map, ValidationContext context) {
        CellProbe probe = context.probe();
        int maxGap = context.maxPassageGap();
        List<Problem> problems = new ArrayList<>();
        if (map.zones().isEmpty()) {
            problems.add(Problem.error(NO_ZONES, "The map has no rooms", null));
            return new ValidationReport(problems);
        }
        checkZones(map, problems);
        checkStartPieces(map, context, problems);
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), probe, maxGap);
        checkDoors(map, passages, problems);
        checkReachability(map, passages, problems);
        Map<BlockPos, SpawnLinks.Link> links = SpawnLinks.resolve(map, context.windows().linkRange());
        checkSpawns(map, links, problems);
        checkWindows(map, links, context, problems);
        problems.addAll(context.itemProblems());
        return new ValidationReport(problems);
    }

    private static void checkWindows(ZombiesMap map, Map<BlockPos, SpawnLinks.Link> links, ValidationContext context, List<Problem> problems) {
        ZoneIndex index = ZoneIndex.of(map);
        for (WindowDef window : map.windows().values()) {
            BlockPos at = window.bottomCenter();
            if (window.width() > context.windows().maxWidth() || window.height() > context.windows().maxHeight()) {
                problems.add(Problem.error(WINDOW_TOO_LARGE, Highlight.of("Window {} is {}, over the {} limit", window.id(), window.width() + "x" + window.height(),
                        context.windows().maxWidth() + "x" + context.windows().maxHeight()), at));
            }
            if (window.zoneBehind(index).isEmpty()) {
                problems.add(Problem.error(WINDOW_UNKNOWN_ZONE, Highlight.of("Window {} doesn't open into a room. The block just inside it, at {}, is in no room. Redraw the room behind it or remove the window",
                        window.id(), window.insideSpot()), at));
            }
            if (links.values().stream().noneMatch(l -> l.window().equals(Optional.of(window.id())))) {
                problems.add(Problem.warning(WINDOW_WITHOUT_SPAWN, Highlight.of("No spawn marker outside window {} (place one within {} blocks of its outer side)",
                        window.id(), context.windows().linkRange()), at));
            }
            for (ValidationContext.MobSize mob : context.mobs()) {
                if (Math.ceil(mob.width()) > window.width() || Math.ceil(mob.height()) > window.height()) {
                    problems.add(Problem.warning(MOB_TOO_BIG_FOR_WINDOW, Highlight.of("Mob profile {} ({}) can't fit through window {} ({})", mob.profile(), mob.width() + "x" + mob.height(),
                            window.id(), window.width() + "x" + window.height()), at));
                }
            }
        }
    }

    private static void checkZones(ZombiesMap map, List<Problem> problems) {
        List<Zone> zones = List.copyOf(map.zones().values());
        for (Zone zone : zones) {
            if (zone.boxes().isEmpty()) {
                problems.add(Problem.error(EMPTY_ZONE, Highlight.of("Room {} has no boxes", zone.id()), null));
            }
        }
        if (map.startZones().isEmpty()) {
            problems.add(Problem.error(NO_START_ZONE, Highlight.of("No room is marked as a start room ({})", "/hs zones set room <room> start true"), null));
        }
        for (int i = 0; i < zones.size(); i++) {
            for (int j = i + 1; j < zones.size(); j++) {
                for (BlockBox a : zones.get(i).boxes()) {
                    for (BlockBox b : zones.get(j).boxes()) {
                        if (a.intersects(b)) {
                            BlockPos at = new BlockPos(Math.max(a.minX(), b.minX()), Math.max(a.minY(), b.minY()), Math.max(a.minZ(), b.minZ()));
                            problems.add(Problem.error(ZONE_OVERLAP,
                                    Highlight.of("Rooms {} and {} overlap", zones.get(i).id(), zones.get(j).id()), at));
                        }
                    }
                }
            }
        }
    }

    /** Players spawn only in a start zone's largest walkable piece, so any other piece is probably a box drawn by mistake. */
    private static void checkStartPieces(ZombiesMap map, ValidationContext context, List<Problem> problems) {
        for (Zone zone : map.startZones()) {
            List<List<BlockPos>> pieces = StandingArea.pieces(zone, context.standable());
            for (List<BlockPos> piece : pieces.subList(Math.min(1, pieces.size()), pieces.size())) {
                problems.add(Problem.warning(SPLIT_START_ZONE, Highlight.of("Start room {} has {} standing spots players can't walk to from the rest of it. Players won't spawn there; remove the box if it's a mistake",
                        zone.id(), piece.size()),
                        piece.get(0)));
            }
        }
    }

    private static void checkDoors(ZombiesMap map, Passages.Result passages, List<Problem> problems) {
        ZoneIndex index = ZoneIndex.of(map);
        ZoneGraph open = ZoneGraph.of(map, passages.open());
        for (DoorDef door : map.doors().values()) {
            if (door.members().isEmpty()) {
                problems.add(Problem.error(EMPTY_DOOR, Highlight.of("Door {} has no blocks. Select some with the Zone Wand, then {}", door.id(), "/hs zones add door " + door.id()), null));
                continue;
            }
            BlockPos at = door.members().iterator().next();
            Set<ZoneId> zones = door.zones(index);
            if (zones.size() < 2) {
                problems.add(Problem.error(DOOR_NOT_BETWEEN_ZONES, zones.isEmpty() ? Highlight.of("Door {} must stand between two rooms, but it borders none", door.id())
                        : Highlight.of("Door {} must stand between two rooms, but it borders only {}", door.id(), String.join(", ", zones.stream().map(ZoneId::value).sorted().toList())), at));
            } else if (door.links(index).stream().allMatch(link -> open.neighbors(link.a()).contains(link.b()))) {
                // Buying the door would unlock nothing: an opening that isn't a door already joins its rooms.
                for (Passages.Link link : door.links(index)) {
                    BlockPos opening = passages.openings().getOrDefault(link, at);
                    problems.add(Problem.error(LOOPHOLE, Highlight.of("Loophole found between {} and {} at {}", link.a(), link.b(), opening), opening));
                }
            }
        }
    }

    private static void checkReachability(ZombiesMap map, Passages.Result passages, List<Problem> problems) {
        List<ZoneId> starts = map.startZones().stream().map(Zone::id).toList();
        if (starts.isEmpty()) {
            return;
        }
        ZoneGraph graph = ZoneGraph.of(map, passages.open());
        Set<Passages.Link> withDoors = new HashSet<>(passages.open());
        ZoneIndex index = ZoneIndex.of(map);
        map.doors().values().forEach(door -> withDoors.addAll(door.links(index)));
        Set<ZoneId> reachable = ZoneGraph.of(map, withDoors).reachableFrom(starts);
        passages.closedDoors().forEach((link, door) -> {
            if (!graph.neighbors(link.a()).contains(link.b())) {
                problems.add(Problem.warning(CLOSED_DOOR_GAP, Highlight.of("Rooms {} and {} are only joined through a closed door. Zombies can't open it: open it, remove it, or make it a door players buy ({})",
                        link.a(), link.b(), "/hs zones add door"), door));
            }
        });
        for (Zone zone : map.zones().values()) {
            if (!reachable.contains(zone.id()) && !zone.boxes().isEmpty()) {
                problems.add(Problem.error(UNREACHABLE_ZONE,
                        Highlight.of("Room {} can't be reached from a start room", zone.id()), null));
            }
        }
    }

    private static void checkSpawns(ZombiesMap map, Map<BlockPos, SpawnLinks.Link> links, List<Problem> problems) {
        Set<ZoneId> zonesWithSpawns = new HashSet<>();
        for (BlockPos spawn : map.features(FeatureKind.SPAWN)) {
            SpawnLinks.Link link = links.get(spawn);
            if (link != null) {
                zonesWithSpawns.add(link.zone());
            } else {
                problems.add(Problem.warning(SPAWN_OUTSIDE_ZONE,
                        "Spawn marker isn't inside a room or in front of a window", spawn));
            }
        }
        for (Zone zone : map.zones().values()) {
            if (!zonesWithSpawns.contains(zone.id()) && !zone.boxes().isEmpty()) {
                problems.add(Problem.warning(ZONE_WITHOUT_SPAWN,
                        Highlight.of("Room {} has no spawn markers", zone.id()), null));
            }
        }
    }
}
