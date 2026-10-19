# Zombie pathfinding

## Goal

Zombies find the shortest walk to their target across many rooms, fast enough for 24 mobs at once. Vanilla pathfinding alone fails over long distances because `maxVisitedNodes` scales with follow range.

## Data shape

```java
record MovementClass(int width, int height, boolean climbs, boolean swims) {}   // bounding box rounded up to whole blocks

final class PassabilityGrid { /* immutable bitset over a zone's bounds: standable, passable, step costs */ }

record PortalEdge(PortalId a, PortalId b, MovementClass mc, int cost, List<BlockPos> nodes) {}

final class PortalPathCache {
    Map<EdgeKey, PortalEdge> edges;                     // (a, b, mc) -> edge, valid for the whole game
    Map<NextKey, PortalId> nextPortalTowardZone;        // (portal, zone, mc) -> next portal, cleared on door open
}
```

Portals are doors, debris, and windows (see `map-model-and-zones.md`).

## Implementation

**Snapshots.** When a zone unlocks (start zone at game start, then the zones beside each opened door), build a `PassabilityGrid` per movement class in use. Legality follows vanilla `WalkNodeEvaluator` rules for steps, jumps, slabs, and fences. The map is immutable during play, so the grid never goes stale and A\* can run on a worker thread against it.

**Intra-zone edges.** On door open, for each zone beside the door, run `BlockAStar` from every portal to every other portal. Store each as one `PortalEdge` with its cost and node list. Edges are never recomputed during a game, because opening a door only adds edges. A game reset clears the cache.

**`BlockAStar`** is pure and runs on a `PassabilityGrid`:
- Axis-aligned moves only (6-connected, plus step-up and drop moves).
- Heuristic: 3D Manhattan distance. With axis-aligned moves every path costs at least the Manhattan distance between its ends, so the heuristic is admissible and paths are optimal.

**Query** (`HierarchicalPlanner`), mob in zone `Zm`, target player in zone `Zp`:
1. `Zm == Zp`: vanilla navigation straight to the player.
2. Otherwise, add a temporary mob node with edges to `Zm`'s open portals and a temporary player node with edges from `Zp`'s open portals. These short edges come from `BlockAStar` inside one zone and are cached for a few seconds.
3. A\* over the portal graph, using cached edge costs as weights and Manhattan distance between portal anchors as the heuristic.
4. Join the chosen edges' node lists into a vanilla `Path` and call `navigation.moveTo(path, speed)`.
5. Replan when the target changes zone or vanilla stuck detection fires. The last leg to the player stays on vanilla navigation, so movement inside a zone never triggers a replan.

**Sharing.** `nextPortalTowardZone` caches the first hop from each portal toward each zone per movement class. With many mobs chasing the same player, most queries become lookups. Opening a door clears this map.

**Fallbacks.** Mobs that don't walk (flyers) or use custom navigators get the portal sequence as waypoints and use their own navigation for each leg.

**Heuristic spike (phase 4).** Compare axis-aligned plus Manhattan against diagonal moves plus 3D octile distance on the test map. Measure path length walked and nodes expanded. Keep Manhattan unless the measurements show a clear difference.

## Config surface

- `pathing.worker_threads` (1).
- `pathing.replan_cooldown_ticks` (10).
- `pathing.local_edge_ttl_ticks` (60).

## Assets

None.

## Depends on

`map-model-and-zones.md`, `doors-and-debris.md`, `windows-and-barricades.md`, `mob-profiles-and-ai-injection.md`.

## Verification

- JUnit `BlockAStar`: optimal path length on hand-made grids (corridor, U-turn, stairs). Manhattan never exceeds the true cost on any tested pair.
- JUnit `HierarchicalPlanner`: on a map where the route through fewer doors is longer, it picks the shorter walk. Opening a door adds edges without recomputing existing ones (call counter).
- JUnit: cache keys separate movement classes, so a 2-wide mob never uses a 1-wide path.
- In game: 24 zombies chase one player across four zones, and the server tick time stays within budget (measured with `/forge tps`).

## Phase

4.
