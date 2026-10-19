package dev.candycanecazoo.packapunch.map.validate;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.CellProbe;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneGraph;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /** Passages are searched this many cells out from each zone unless the caller says otherwise. */
    public static final int DEFAULT_MAX_GAP = 2;

    private MapValidator() {}

    /** Validates without looking at the world: only zones whose boxes touch are connected. */
    public static ValidationReport validate(ZombiesMap map) {
        return validate(map, CellProbe.ALL_SOLID, DEFAULT_MAX_GAP);
    }

    public static ValidationReport validate(ZombiesMap map, CellProbe probe, int maxGap) {
        List<Problem> problems = new ArrayList<>();
        if (map.zones().isEmpty()) {
            problems.add(Problem.error(NO_ZONES, "The map has no zones", null));
            return new ValidationReport(problems);
        }
        checkZones(map, problems);
        checkReachability(map, Passages.find(map, ZoneIndex.of(map), probe, maxGap), problems);
        checkSpawns(map, problems);
        return new ValidationReport(problems);
    }

    private static void checkZones(ZombiesMap map, List<Problem> problems) {
        List<Zone> zones = List.copyOf(map.zones().values());
        for (Zone zone : zones) {
            if (zone.boxes().isEmpty()) {
                problems.add(Problem.error(EMPTY_ZONE, "Zone '" + zone.id() + "' has no boxes", null));
            }
        }
        if (map.startZones().isEmpty()) {
            problems.add(Problem.error(NO_START_ZONE, "No zone is marked as a start zone (/pap zone start <zone>)", null));
        }
        for (int i = 0; i < zones.size(); i++) {
            for (int j = i + 1; j < zones.size(); j++) {
                for (BlockBox a : zones.get(i).boxes()) {
                    for (BlockBox b : zones.get(j).boxes()) {
                        if (a.intersects(b)) {
                            BlockPos at = new BlockPos(Math.max(a.minX(), b.minX()), Math.max(a.minY(), b.minY()), Math.max(a.minZ(), b.minZ()));
                            problems.add(Problem.error(ZONE_OVERLAP,
                                    "Zones '" + zones.get(i).id() + "' and '" + zones.get(j).id() + "' overlap", at));
                        }
                    }
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
        Set<ZoneId> reachable = graph.reachableFrom(starts);
        passages.closedDoors().forEach((link, door) -> {
            if (!graph.neighbors(link.a()).contains(link.b())) {
                problems.add(Problem.warning(CLOSED_DOOR_GAP, "Zones '" + link.a() + "' and '" + link.b()
                        + "' are only joined through a closed door. Zombies can't open doors: open it, remove it, or make it a Pack-a-Punch door", door));
            }
        });
        for (Zone zone : map.zones().values()) {
            if (!reachable.contains(zone.id()) && !zone.boxes().isEmpty()) {
                problems.add(Problem.error(UNREACHABLE_ZONE,
                        "Zone '" + zone.id() + "' can't be reached from a start zone", null));
            }
        }
    }

    private static void checkSpawns(ZombiesMap map, List<Problem> problems) {
        ZoneIndex index = ZoneIndex.of(map);
        Set<ZoneId> zonesWithSpawns = new HashSet<>();
        for (BlockPos spawn : map.features(FeatureKind.SPAWN)) {
            Optional<ZoneId> zone = index.zoneAt(spawn);
            if (zone.isPresent()) {
                zonesWithSpawns.add(zone.get());
            } else {
                problems.add(Problem.warning(SPAWN_OUTSIDE_ZONE,
                        "Spawn marker isn't inside a zone or linked to a window yet (window links come with windows)", spawn));
            }
        }
        for (Zone zone : map.zones().values()) {
            if (!zonesWithSpawns.contains(zone.id()) && !zone.boxes().isEmpty()) {
                problems.add(Problem.warning(ZONE_WITHOUT_SPAWN,
                        "Zone '" + zone.id() + "' has no spawn markers", null));
            }
        }
    }
}
