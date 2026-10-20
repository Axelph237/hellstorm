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

## Flow fields (replaced the hierarchical planner, after play testing)

In play, zombies reached the end of the planned route and stopped. Two pathfinders disagreed about where a zombie can walk: our planner picked a doorway, but vanilla navigation couldn't always reach it and quietly returned a partial path. Every zombie also followed the same route in single file. The hierarchical planner, its edge cache, and `PortalNavigator` were removed and replaced:
- **`FlowGraph`** (`mob/path`). It covers every walkable node of all unlocked zones plus the doorway cells between them, stored as forward and reverse edge arrays. It is immutable. (First built from our own grid and `BlockAStar`'s moves, now from vanilla's evaluator; see below.)
- **`FlowField`** (one per chased player). A reverse Dijkstra from the player's cell gives every node's true walking cost to the player, drops included (they're one-way). Unit tests check it against a reference Dijkstra.
  - **`next`** steps to the cheapest neighbour. Ties are split per zombie, so a crowd fans out.
  - **`lookahead`** picks the farthest downhill cell reachable in a straight line on the same level, so zombies cut corners instead of following the grid's staircase.
- **`GamePathing`.** Graphs are built on the server thread the first time a zombie of that size needs one. A player's field is recomputed on the game's worker thread when the player moves to another cell, at most once every 5 ticks. Zombies always read the latest finished field and never wait.
- **`FlowNavigator`** (one per zombie, run every tick).
  - **Steering.** It moves straight at the lookahead point with the move control, plus a fixed per-zombie sideways offset.
  - **Close range.** Within 2 blocks with line of sight, it goes straight for the target.
  - **Doors.** It opens a closed wooden door in its next two cells.
  - **Stuck.** It falls back to vanilla navigation for 2 seconds if it hasn't moved half a block in 2 seconds, or while no field is ready.
- **Doorway portals** (`ZonePathing.portals`) remain for `/pap map paths` and `show_paths`. They no longer drive movement.
- **`show_paths`** draws each zombie's downhill walk from its current cell.
- **What one field costs.** One Dijkstra per player per cell change, shared by every zombie chasing that player, so cost doesn't grow with the number of zombies.

## Graphs from vanilla's walk evaluator (after play testing)

Zombies walked into glass panes, trapdoors, and door panels. Our own grid called a cell open whenever vanilla's `isPathfindable(LAND)` said so, which is true for panes and open trapdoors that a mob can't actually step through. Two models of walkability disagreed again, so the flow graph now comes from vanilla's:
- **`VanillaGraphs`** builds one `FlowGraph` per entity type. It sets up a `WalkNodeEvaluator` for a real mob of that type (doors passable and openable, floating on), seeds it with every standable cell of the unlocked zones, and floods with the evaluator's own `getNeighbors`. Neighbours outside the unlocked zones and their doorway cells are dropped. Edge cost is the step length (diagonals 1.41) plus height change plus vanilla's path malus, times 10.
- **What this buys.** Panes, bars, fences, walls, trapdoors, doors, slabs, carpet, and modded blocks behave exactly as vanilla pathfinding sees them, for that mob's size and penalties. GameTested with a wall of panes, bars, and fences that has a gap: wall cells aren't nodes and the walking cost goes round through the gap.
- **Cost.** Building is on the server thread the first time a zombie of that type needs a graph. Test rooms (about 80 nodes) take 2 to 9 ms. A full-size map hasn't been measured; the build stops at 400,000 nodes.
- **Who uses it.** Only mobs whose navigation uses a `WalkNodeEvaluator` and whose profile says `navigation: flow`. Everything else uses its own vanilla navigation with a 128-block follow range (see `mob-profiles-and-ai-injection.md`).
- **Removed.** `ZonePathing.unionGrid`. The per-zone grids remain only for the doorway portals in `/pap map paths`.

## Real collision checks (after play testing)

Zombies still walked into open trapdoors and clipped door jambs. Vanilla's walk evaluator counts every open trapdoor cell as walkable whichever side the panel is on, and our steering checked straight lines through block centres only.
- **Graph edges are swept.** `VanillaGraphs` slides the mob's box from one node's centre to the next, above step height, against real block shapes, and drops moves that touch anything. Moves through cells with no shapes at all skip the sweep. A wall of open trapdoors is now a wall.
- **Steering is swept.** `FlowNavigator` picks the farthest downhill cell on its level that its own box can slide to from where it actually stands, checked with the level's collision. The sideways spread is only applied when that is clear too. If not even the next cell is reachable directly, it re-centres in its own cell first. The aim is re-chosen every 4 ticks or when reached. `FlowField.lookahead` (grid-only) was removed.
- **Stalls are counted.** `GamePathing.stalls()` counts the times a zombie stopped making progress and needed vanilla navigation. The chase GameTests require zero, including a decoy wall of open trapdoors beside the real doorway and a doorway at the end of a wall that forces a tight turn.

## Performance (phase 7)

Measured on the Test map in a live client: one player (god mode), round 1 forced to 200 zombies with `spawn_delay_ticks = 2`, so the round sat at the 24-alive cap. Server average tick time (`MinecraftServer.getAverageTickTime`, a 100-tick rolling average):

| State | Tick time |
|---|---|
| Lobby and countdown | 1.8 to 3.6 ms |
| Spawning in, 0 to 24 zombies (first 4 s) | peak 9.0 ms |
| 24 zombies chasing, steady (80 s) | 1.8 to 2.8 ms, typically 2.1 ms |

Out of a 50 ms budget, so no optimization was needed. The spawn-time peak is the path graphs being built for the first mobs of a type. More players add only their HUD sync (every 4 ticks) and spawn-candidate weighting; zombie count is what drives the cost. A rolling average can hide a single long tick; single-tick spikes weren't measured.
