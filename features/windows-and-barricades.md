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
