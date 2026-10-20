# Windows and barricades

## Goal

Zombies enter the playable area through boarded windows. They tear boards off, players repair them for points. Windows can be any size the map maker wants, up to a configured maximum.

## Data shape

- `barricade` block with blockstate `boards` (0 to 6) and `facing`.
- `WindowController` BlockEntity:

```java
record WindowState(
    Direction facing,
    int width, int height,          // 1x1 up to windows.max_width x windows.max_height
    List<BlockPos> members,
    int maxBoards,                  // per window, default windows.default_boards
    int boards) {}
```

## Implementation

- **Placement.** The Window Tool fills a W×H opening on one wall face with `barricade` blocks and puts the `WindowController` at the bottom-left member. Every member stores the controller's position.
- **Shared board state.** The controller owns `boards`. On every change it writes the `boards` blockstate to every member, so all of them render the same number of boards. With `maxBoards` other than 6, the blockstate value is scaled: `round(boards * 6 / maxBoards)`.
- **Board model.** A multipart block model with one cuboid per board, each referencing `minecraft:block/oak_planks`. Board `n` appears when `boards >= n`.
- **Collision.** `getCollisionShape(state, level, pos, CollisionContext)` returns empty when `boards == 0` and the context entity carries the `GameMob` capability. Otherwise it returns the full shape. Players can never walk out through a window.
- **Breaking in.** `BreakBarricadeGoal` walks a game mob to the controller's outside face. It removes one board every `windows.tear_ticks` while in range. At 0 boards the mob climbs through and switches to `PortalNavigateGoal`.
- **Repairing.** A player holding use near a window with missing boards adds one board every `windows.repair_ticks` and earns repair points (see `economy-and-points.md`), capped per round.
- **Carpenter** power-up sets every window to `maxBoards`.
- **Validation.** For each mob profile that can spawn at a window, `MapValidator` checks the mob's bounding box against the opening (see `map-validation.md`).

## Config surface

- `windows.max_width` (default 5), `windows.max_height` (default 4).
- `windows.default_boards` (default 6), overridable per window.
- `windows.tear_ticks` (default 30), `windows.repair_ticks` (default 15).

## Assets

- Boards use vanilla `minecraft:block/oak_planks` in a custom model. No new texture.

## Depends on

`map-model-and-zones.md`, `zone-tools-and-outlines.md`, `mob-profiles-and-ai-injection.md`.

## Verification

- GameTest on a 1×1, a 3×2, and a max-size window:
  - All members show the same board count after each tear and repair.
  - A game mob can't pass while boards are above 0 and can pass at 0.
  - A player can't pass at any board count.
- GameTest: Carpenter restores every window to full.
- In game: a zombie tears a 3×2 window down board by board and climbs in.

## Phase

4.

## As built (phase 4)

- **Window data lives in the map,** the same way door data does. A `WindowDef` holds the id, the opening box, the outward facing, the max boards, and the inside zone, and lives in `ZombiesMap`. It doesn't use a `WindowController` BlockEntity. The barricade blocks stay plain. The current board count is their shared `boards` blockstate, which `WindowOps` reads and writes.
- **No separate Window Tool.** Select the hole with the Zone Wand: a corner is the air cell in front of the face you click, which is exactly the opening. Then run `/pap window add <id> [boards]`. The facing is inferred: the side of the opening that lies in a zone is the inside. The opening must be one block thick, within `windows.max_width` / `max_height`, and must not overlap another window. Also `/pap window remove <id>` and `/pap window list`.
- **Collision depends on the entity.** Game mobs pass at 0 boards. Projectiles always pass (GameTested with a vanilla arrow), so players should be able to shoot through windows. Whether TacZ bullets hit blocks through the same per-entity collision is not yet checked in game. Players and other mobs are always blocked.
- **Boards face the zone.** They sit on the opening's inside face. The block's outline and its collision for anything blocked are a 3/16-thick panel there, like a trapdoor, rather than the whole block. Barricades are never pathfindable for vanilla navigation; zombies climb in with `BreakBarricadeGoal`.
- **Zombies spread along a window.** Each zombie picks its own column of the opening, plus a small random offset, for where it stands to tear boards. Its attack and inside points drop to the ground, so windows with a sill work. Several zombies can work one window at once.
- **Context-free collision.** Queries with no entity (vanilla's move control, for one) see a bare window as open. Otherwise zombies climbing through tall windows kept jumping at the bottom block. Room detection and passage finding treat barricades as walls regardless of boards.
- **No suffocation.** Barricades are non-suffocating. Vanilla's in-wall check reads collision without the entity, so zombies climbing through a bare window took suffocation damage every tick (GameTested).
- **Board models** are generated by `tools/gen_barricade_models.py` and use only the vanilla oak planks texture.
- **Spawn markers outside zones** auto-link to the nearest window they stand in front of, within `windows.link_range` (default 6). Zombies from those markers get `BreakBarricadeGoal`. The visualizer draws a line from each marker to its window.
- **Repair.** A player holding use on a barricade repairs one board per `windows.repair_ticks`, earning `economy.repair` points up to `economy.repair_cap_per_round` per round.
- **Reset.** Windows are reset to full boards when a game's lobby opens and when the game ends.
