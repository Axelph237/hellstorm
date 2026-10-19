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

## As built (phase 4)

- **Pure core** in `mob/path`: `PassabilityGrid` (frozen bitsets), `BlockAStar`, `PortalPathCache`, and `HierarchicalPlanner` (immutable graph snapshot, so `plan` needs no lock). Move costs equal their Manhattan length, so the heuristic is admissible and consistent.
- **Heuristic spike, measured (`HeuristicSpikeTest`):**
  - Manhattan expanded about as few nodes as possible in open rooms (46 nodes for a 46-block path).
  - Octile's paths were 20 to 30% shorter in open space on paper, but it expanded up to 2.7 times as many nodes among pillars.
  - Vanilla path following already smooths Manhattan's corners, so we keep Manhattan.
- **Portals from the world** (`ZonePathing`). `Passages` now records each connection's gap cells, meaning the open cells both zones' searches reached. A zone's grid covers its own cells plus its gap cells.
  - **A doorway is a gap cell** that is walkable in both grids and steps sideways onto a walkable cell of each zone. Open air above the rooms or around the ends of a wall is excluded, because it's shared gap but no doorway. A doorway becomes a zero-cost portal.
  - **Zones whose boxes touch** get a portal between two adjacent walkable cells instead.
  - **One portal per connection for now.**
- **Runtime** (`GamePathing`, one per game):
  - Grids for a movement class are built on the server thread the first time a mob that size spawns. Plans then run on the game's single daemon worker thread.
  - `PortalNavigator` (one per mob) uses vanilla navigation inside the target's zone. Across zones, the plan only picks the **next doorway**: the first planned cell outside the mob's current zone. Vanilla navigation then walks there from the mob's own position. It replans when the mob or the target changes zone, and uses vanilla navigation while a plan is pending or missing.
  - **Why only the doorway.** Following the whole planned block path made every zombie queue along the same line. Vanilla re-paths from each mob's position and cuts corners, so a crowd spreads out and pushes past itself.
  - The worker shuts down with the game.
- **Not built yet.** The "next portal toward zone" cache isn't built yet. With small portal graphs, plans are cheap enough without it.
