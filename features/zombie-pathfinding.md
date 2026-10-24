# Zombie pathfinding

## Goal

Zombies find their way to their target across many rooms, fast enough for 24 mobs at once. Vanilla pathfinding handles a short walk well, but over long distances it runs out of nodes, and it walks into open trapdoors and door jambs.

## Data shape

```java
class GameNodeEvaluator extends WalkNodeEvaluator { /* vanilla's neighbours, minus swept-box collisions and unbought door blocks */ }

final class FlowGraph { /* immutable: every cell vanilla navigation can walk, with the moves between them */ }

final class FlowField { /* immutable: walking cost from every node of a graph to one goal */ }
```

## Implementation

- **One walkability model.** `GameNodeEvaluator` is vanilla's walk evaluator with two kinds of move taken away: moves the mob's box can't make (it slides the box against real block shapes, so a wall of open trapdoors is a wall), and moves into the blocks of a door nobody has bought (a wooden door or a cobweb there is still a wall). Game zombies' own navigation (`GameNavigation`) and the distance fields both use it, so a point the guide picks is one vanilla navigation can walk to.
- **Near: vanilla.** Within 20 blocks' walk of its target, a zombie paths straight at it with `navigation.moveTo(target)`, every half second. Its navigation follows the path the way vanilla does, except that it turns a corner only once its box can slide straight on, so it doesn't catch on doorway jambs.
- **Far: a point down the field.** Farther out, it paths to the cell 16 blocks' walk down its target's distance field, picked again every half second as it goes. Vanilla only ever plans a short walk, and each zombie plans its own from where it stands.
- **Rooms play no part.** The graph covers every cell a mob can walk to from the unlocked rooms, inside the map's bounds. A zombie in a doorway, in a gap between two rooms' boxes, or on a stair at a room's edge is on the graph like anywhere else.

## Config surface

None. The near distance (20 blocks) and the leg length (16 blocks) are constants in `GamePathing`.

## Assets

None.

## Depends on

`map-model-and-zones.md`, `doors-and-debris.md`, `windows-and-barricades.md`, `mob-profiles-and-ai-injection.md`.

## As built (2026-10-24)

Play testing showed zombies struggling where two rooms meet. Every earlier design moved zombies through rooms by their own route (portal hops first, then steering cell by cell down a flow field with the move control), and each time a second model of where a zombie can walk disagreed with vanilla's. Zombies now walk only on vanilla navigation, and the game's part is to hand a far zombie somewhere nearer to walk to.

- **`GameNodeEvaluator`** (`game/`). Overrides `getNeighbors` and drops a neighbour when the mob's box, swept from one node's centre to the next above step height, touches a block shape, or when the mob's box at the neighbour would hold a block of an unbought door. A closed wooden door the mob can open doesn't count as a shape. Moves through cells with no collision shapes skip the sweep. The sweep is the check the flow graph used before (it caught open trapdoors whose panel faces the way, and door jambs); it now runs inside vanilla's own search.
- **`GameNavigation`** (`game/`). A `GroundPathNavigation` whose path finder uses `GameNodeEvaluator`. `ForgeGameWorld.spawnZombie` installs it on game mobs whose profile says `navigation: flow` and whose navigation is plain `GroundPathNavigation` (zombies, wolves); a mob with its own kind of navigation keeps it. It is built after the 128-block follow range is set, so its node budget is already 2048. It needs `Mob.navigation` public (access transformer).
- **`VanillaGraphs`** builds one `FlowGraph` per entity type by flooding with `GameNodeEvaluator.getNeighbors` from every standable cell of the unlocked zones, kept within the bounding box of all zones plus 2 blocks. It no longer reads `Passages` or limits the flood to zone boxes and doorway cells.
- **`FlowField.ahead(from, cost, tieBreak)`** walks downhill until the cell is `cost` nearer the goal (or is the goal). Ties are split per zombie.
- **`GamePathing`** (one per game) keeps the graphs, one field per chased player and mob type (recomputed on the game's worker thread when the player moves to another cell, at most every 5 ticks; zombies read the latest finished one), and `waypoint(mob, target, tieBreak)`: empty within `NEAR` (20 blocks' walk) of the target, off the graph, or before the first field is ready, otherwise `ahead` by `LEG` (16 blocks). A door bought calls `doorOpened(unlocked, openDoors)`, which drops the graphs and fields. The worker shuts down with the game, and the `!worker.isShutdown()` guard keeps a zombie left from an ended game from queueing work.
- **`GameNavigation` follows a path with collision in mind.** Vanilla turns for the node after next as soon as it is within half a block of the next one, or heading past it, which walks a zombie into the jamb of a doorway it turns into. `GameNavigation.followThePath` turns only when the mob's box can slide straight to the node after next (or stands on the next node's centre), and a mob the crowd shoves off that line aims back at the node it skipped. Steps up and down keep vanilla's rule.
- **`GuidedNavigator`** (one per zombie). Every 10 ticks it asks for a waypoint and calls `moveTo` on it, or on the target when there is none or vanilla finds no path to the waypoint. A zombie that hasn't got half a block anywhere for 15 ticks, with another zombie chasing the same player shoving it from nearer the player, stands for 15 ticks so the other gets through. It counts a stall (`GamePathing.stalls()`) when the zombie goes 40 ticks without getting half a block anywhere while more than 3 blocks from its target.
- **What stayed.** `BreakBarricadeGoal` runs first, so a zombie still goes to its window, tears the boards off, and climbs in before it chases. Targeting skips downed players. `navigation: vanilla` profiles keep the mob's own navigation, straight at the target, and so do mobs that don't walk.
- **Removed.** `FlowNavigator` (move-control steering, its door opening, sideways spread, lookahead, and vanilla rescue), the per-zone `PassabilityGrid`, `BlockAStar`, `ZonePathing`, `Portal`, `CellQuery`, `GridPath`, and their tests, including the heuristic spike. `GamePathing` no longer takes `Passages.Result`. `/hs map paths` and the `show_paths` connection boxes now come straight from `Passages` (one crossing cell per connection), and `show_paths` draws every zombie's vanilla path.

### Measured

GameTests in `ZoneBoundaryGameTests` send four zombies from one room into the next, where the two rooms' boxes touch, to a player standing past the boundary. Each records when every zombie reached the player and the longest any of them stood still (not getting half a block anywhere) on the way, and fails if that reaches 40 ticks.

GameTests in `ZoneBoundaryGameTests` send zombies from one room into the next, where the two rooms' boxes touch, to a player standing past the boundary. Each logs when every zombie reached the player and the longest any stood still (not getting half a block anywhere) on the way. Six runs of each, on the steering this replaced and on this design:

| Test | Zombies | Last one in, before (ticks) | Last one in, after (ticks) | Deadline |
|---|---|---|---|---|
| Doorway 1x2 in a stone wall | 4 | 159 to 243 | 141 to 158 | 200 |
| The same doorway, crowded | 8 | 198 to 360 | 152 to 207 | 260 |
| Wall of open trapdoors with a gap (the Test map's boundary) | 4 | 113 to 184 | 121 to 153 | 200 |
| The Test map's start, hall, and room, copied block for block | 4 | 157 to 176 | 167 to 170 | 220 |

- **Where the old steering lost time.** At the doorway: two zombies arriving together pinned each other against its jambs, standing still for up to 66 ticks with four zombies and 76 with eight. The deadlines fail it in 2 and 3 of the 6 runs.
- **Plain vanilla navigation isn't enough.** With the mob's own navigation (no `GameNavigation`), all four zombies pressed into the trapdoor wall and never reached the player in 900 ticks, the Test map copy timed out the same way, and `aZombieGoesRoundATrapdoorWallToTheRealDoorway` failed.
- **Vanilla's path following jams in doorways.** With `GameNodeEvaluator` but vanilla's own path following, two zombies side by side at the doorway each aimed past it and pressed into a jamb, standing still for 450 to 556 ticks in half the runs. That is why `GameNavigation` turns only when the box can slide straight to the next node, steps back when shoved off the line, and why a zombie behind another yields.
- **Queueing still shows.** One doorway takes one zombie at a time, so the zombie that waits its turn stands still for 40 to 50 ticks with four and up to 83 with eight. The tests allow 100 ticks standing still.

## History

- **Phase 4: hierarchical planner.** A\* over portals (doorways found from `Passages` gap cells) with cached portal-to-portal edges from our own grid A\*. In play, zombies reached the end of the planned route and stopped: our planner picked a doorway vanilla couldn't always reach.
- **Flow fields with steering.** One graph over all unlocked zones plus doorway cells, a field per player, and zombies steered at a lookahead cell with the move control. The graph moved to vanilla's evaluator after zombies walked into panes and trapdoors, and gained swept collision checks after they clipped trapdoors and jambs. Zombies still had to cross rooms through the doorway cells `Passages` found, and steered by our own rules rather than vanilla's path following.
- **Performance (phase 7, flow fields with steering).** On the Test map with 24 zombies chasing one player, the server's average tick time was 1.8 to 2.8 ms, with a 9 ms peak while the first graphs were built. Not yet measured for this design; vanilla's search now also runs the swept check per neighbour.
