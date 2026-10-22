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
- **Board model.** One set of long boards for the whole window, drawn by a client renderer (see As built).
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
- **No separate Window Tool.** Select the hole with the Zone Wand: a corner is the air cell in front of the face you click, which is exactly the opening. Then run `/hs window add <id> [boards]`. The facing is inferred: the side of the opening that lies in a zone is the inside. The opening must be one block thick, within `windows.max_width` / `max_height`, and must not overlap another window. Also `/hs window remove <id>` and `/hs window list`.
- **Collision depends on the entity.** Game mobs pass at 0 boards. Projectiles always pass (GameTested with a vanilla arrow), so players should be able to shoot through windows. Whether TacZ bullets hit blocks through the same per-entity collision is not yet checked in game. Players and other mobs are always blocked.
- **Boards face the zone.** They sit on the opening's inside face. The block's outline and its collision for anything blocked are a 3/16-thick panel there, like a trapdoor, rather than the whole block. Barricades are never pathfindable for vanilla navigation; zombies climb in with `BreakBarricadeGoal`.
- **Zombies spread along a window.** Each zombie picks its own column of the opening, plus a small random offset, for where it stands to tear boards. Its attack and inside points drop to the ground, so windows with a sill work. Several zombies can work one window at once.
- **Context-free collision.** Queries with no entity (vanilla's move control, for one) see a bare window as open. Otherwise zombies climbing through tall windows kept jumping at the bottom block. Room detection and passage finding treat barricades as walls regardless of boards.
- **No suffocation.** Barricades are non-suffocating. Vanilla's in-wall check reads collision without the entity, so zombies climbing through a bare window took suffocation damage every tick (GameTested).
- **Boards span the whole window.** A window shows `maxBoards` boards in total, not that many per block. `BoardLayout` (pure, unit-tested) places each one across the full width at a slant, `OVERHANG` (0.15) past each side, spread from bottom to top, with alternating slant directions on two depth layers. Boards near the top and bottom lie flatter so their ends stay inside the opening. The layout is seeded by the window id, so it is random per window but stable between frames, reloads, and players. Board `i` shows while `boards > i`, so the last nailed on is the first torn off.
- **Drawn by `client/BoardRenderer`,** not block models. It runs at `RenderLevelStageEvent` `AFTER_BLOCK_ENTITIES` for windows within 128 blocks and in view, reading the synced map and the `boards` blockstate. Boards sit on the room side of the wall face, so their overhang shows on the wall. Each board shows one plank row of the vanilla `minecraft:block/oak_planks` texture stretched along its full length. The barricade block model is empty apart from its particle texture. No new textures.
- **Spawn markers outside zones** auto-link to the nearest window they stand in front of, within `windows.link_range` (default 6). Zombies from those markers get `BreakBarricadeGoal`. The visualizer draws a line from each marker to its window.
- **A window feeds the zone behind it.** Its markers feed the zone holding the cell just inside the opening (`WindowDef.zoneBehind`), read from the zones as they are now. The `zone` stored at `/hs window add` is only where it opened then: a window made while one zone covered two rooms kept feeding that zone after the back room was redrawn as a locked zone of its own, so its zombies spawned from round 1 and broke into the locked room. A window with no zone behind it feeds nothing and is the `window_unknown_zone` error. JUnit `SpawnLinksTest`, `MapValidatorTest.windowChecks`.
- **Repair.** A player holding use on a barricade repairs one board per `windows.repair_ticks`, earning `economy.repair` points up to `economy.repair_cap_per_round` per round.
  - **A board takes its time.** Holding use doesn't put a board on at once: `WindowOps.tryRepair` (from right click or `UsePacket`) only tells the game the player is holding use on that window (`Game.holdRepair`). The game ticks the hold, and once it has lasted `repair_ticks` the board goes on (`GameWorld.addBoard`) and the next one starts from there, so the boards come exactly `repair_ticks` apart. Like a revive, a hold survives 5 ticks between presses, which covers right click's repeat (every 4 ticks); a longer let go, or turning to another window, starts the board over. A board due within those 5 ticks still goes on. A hold ends when the window is full.
  - **Progress bar.** The hold shows on the repairer's HUD as a "Rebuilding" bar that fills over `repair_ticks`; the board goes on as it fills, and it starts again for the next board (see `hud-and-prompts.md`).
  - **Prompt.** Looking at a window with boards missing shows "Rebuild barricade" (`PromptOverlay`), on the window's inside face, 0.35 out so the boards don't cover it.
  - **Sounds.** A board going on plays vanilla's `entity.armor_stand.break` (a wooden clatter) from the middle of the opening, for everyone nearby. It replaced `block.wood.fall`, which replaced `block.wood.place`. When the board pays, the repairer alone also hears the cha-ching (`GameWorld.chaChing`, the purchase sound, see `purchasables-foundation.md`); once the round's repair cap is reached, boards stop paying and so stop the cha-ching.
  - **Tests.** JUnit `RepairTest`: the board goes on the tick the bar fills, the bar's progress and rate, right click's repeat counts as holding, letting go or turning away starts over, the cap stops the pay and the cha-ching, a full window ends the bar. GameTest `holdingTheRepairKeyRebuildsABoardEachTimeTheBarFills`: the key's packet every 2 ticks puts on one board per `repair_ticks` with the bar about half full halfway, the board pays, the repairer hears one cha-ching and a teammate none, and letting go stops it.
  - **Check in game** (not covered): the bar filling smoothly and the board appearing as it fills, with right click and with the use key; the prompt's place on the window; the thud and the cha-ching.
- **Use key.** Right click aims a TacZ gun, so there's also a "Use (repair windows, buy)" key (Controls, Hellstorm category). It defaults to O, the same key as TacZ's Interact, and can be rebound. Holding it while looking at a barricade sends `UsePacket` every 2 ticks; the server checks reach and keeps the repair hold going, so it repairs at the same pace and for the same points as holding use. On a machine it buys once per press.
- **Reset.** Windows are reset to full boards when a game's lobby opens and when the game ends.
- **Sills.** At 0 boards a zombie first aims at the top of the sill in its column, then at the inside floor. A target above its feet is what makes vanilla's move control jump, so windows set a block (or a stair or slab) above the ground outside work. Standing points use the real height of the block underneath. GameTested with a full-block sill and a sideways stair sill under a 3-high corridor.
