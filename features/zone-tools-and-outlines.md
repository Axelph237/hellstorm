# Zone tools and Create-style outlines

## Goal

Map makers draw zones (rooms, windows, and doors) with a tool that feels like Create's super glue: a box that follows the cursor, nearby zones outlined while the tool is held, and the hovered room highlighted.

## Data shape

- **Zones** is the umbrella word for the three things the wand draws: rooms, doors, and windows. What older versions and the Java code call a zone is a room. The Java types keep their names (`Zone`, `ZoneId`, `ZoneIndex`), and so do config keys and the map codec's field names, so saved worlds and map packs load unchanged.
- One tool item, the Zone Wand (`zone_wand`). Rooms, doors, and windows are all made from its selection (see `doors-and-debris.md` and `windows-and-barricades.md`).
- **Selection is server-authoritative.** Clicks are handled on the server (`ToolState`), and each change is mirrored to the client (`SelectionPacket`). The client only draws it, plus the live corner under the cursor.
- Commands, all under `/hs zones`:
  - `/hs zones add room <room> [<start>]` adds the selection as a box. On an existing room it adds another box, so a room is the union of its boxes (L-shapes, corridors with side rooms).
  - `/hs zones add door <door> [<cost>]` and `/hs zones add window <window> [<boards>]` (see the door and window docs).
  - `/hs zones cut [<room|door> <name>]` takes the selection out of every room and door it touches, or only the named one.
  - `/hs zones merge [<from> <into>]` joins rooms, or doors, into one.
  - `/hs zones remove <room|door|window> <name>`, `/hs zones list`, `/hs zones set room <room> start <true|false>`.
  - `/hs zones detect <room>`, then `/hs zones confirm` or `/hs zones cancel`.
  - `/hs undo` and `/hs redo` step through the history of these edits.
- **Settings given to `add` apply only when it creates the thing.** A new room gets `start` (default false), a new door its cost, a new window its boards. Adding to one that exists keeps its settings; `/hs zones set` changes them.

## Implementation

**Outline renderer.** `client/outline/Outlines`, ported from the Outliner in Catnip (Ponder 1.0.92, MIT), which this used to depend on. `client/outline/ZoneOutlines` is the map tools' face onto it.
- Each outline sits in a slot and lives one client tick after it was last shown or kept, then fades over 8 ticks and is removed. Callers show their outlines every client tick while a tool is held.
- Lines are thin solid boxes, not GL lines (GL line width does nothing on macOS): a box's 12 edges, a line turned from +z to point from its start to its end, or a cluster's edges. They're drawn after particles with vanilla's `entitySolid` render type (the same shader, culling, and lighting as Catnip's `outline_solid`) on a white pixel made in memory (`client/WhiteTexture`), full bright.
- A box's edges are pushed 1/128 out from a camera outside it, and pulled 1/128 in from one inside, so the walls they lie on don't hide them.
- A cluster's edges are where its surface folds: each block toggles its 12 edges, so edges two or four blocks share cancel out, and the outside of a corner and the inside of a bend stay.
- While fading, an outline's lines thin with alpha cubed, and it isn't drawn once that is under 1/8.

**Behavior** (modeled on Create's `SuperGlueSelectionHandler`):
- While the wand is held, every room within range draws as one cluster outline around the union of its boxes (built once per geometry change and kept alive with `keep`). Rooms over 65,536 blocks fall back to one outline per box. The color comes from a hash of the room id, so it stays stable. Lines are 1/64 wide.
- The room under the cursor (found by ray-clipping its boxes) gets 1/16 lines.
- First click sets a corner. Each tick the box from that corner snaps to the hovered block. It turns yellow (`0xc5b548`) when out of reach or overlapping another room. Boxes already selected stay drawn, yellow where they overlap a room.
- Doors draw as gold cluster outlines, windows as boxes in their room's color, spawn markers as small boxes with a line to their window.
- Validation problems with positions draw in red.

**Room detection** (`/hs zones detect <room>`):
1. Flood-fill air from the player's position (or the cell above it when the player stands in a low floor block such as a carpet or slab), stopping at solid blocks, door members whatever they're made of, barricade blocks, and cells already in any room; low floor blocks (carpets, bottom slabs, stairs, paths, farmland, snow layers) directly under the fill join the room but never spread it, up to `tools.detect_max_volume`. Standing inside an existing room is refused with a message naming that room.
2. If the cap is hit, report "room is not closed" and stop.
3. Merge the voxel set into AABBs with greedy box merging (grow along x, then z, then y).
4. Preview the merged boxes and confirm with `/hs zones confirm` (or `/hs zones cancel`). The preview sends boxes rather than every cell, because a 32k-cell room is too large for one packet.

## Config surface

- `outline_range` (client, default 64 blocks).
- `tools.detect_max_volume` (default 32768 blocks).
- `tools.reach` (default 32 blocks).

## Assets

- None. Lines and room faces are vertex colors on a white pixel made in memory.
- **Needed from you:** a face texture for the hovered room highlight. Until then, hover shows thicker lines only. Listed in `TEXTURES_NEEDED.md`.

## Depends on

`map-model-and-zones.md`, `map-validation.md`.

## Verification

- JUnit: greedy box merging covers exactly the input voxels with no overlaps, on an L-shaped room and a room with a pillar.
- JUnit: flood fill stops at the volume cap on an open map.
- In game: holding the wand shows both test rooms in distinct colors, hovering thickens one, and the selection box follows the cursor and turns yellow on overlap.
- Launch with Create 6.0.8 installed: Create brings its own Ponder, and Hellstorm's outlines still draw.

## Phase

1.

## As built

- **A corner is the block you're looking at** (`ZoneWandItem.targetCell`). It used to be the air cell in front of the clicked face; looking at a door's or window's own blocks is easier. Clicking a room's floor and ceiling takes them into the room. Rooms can't overlap, so two rooms sharing a wall can't both take it in. To select a window's hole, fill it with any blocks first. `/hs zones add window` replaces them with boards.
- **A selection is many boxes** (`tools/Selection`). Every two clicks add a box. A box that makes one box with one already selected (beside it and the same size across, overlapping it the same way, inside it, or around it) is merged into it, repeatedly, so a box that bridges two others joins all three. Other boxes are kept apart. The selection lasts until it is used or cleared: a successful add, cut, or selection merge clears it, and so do sneak-click and `/hs zones cancel` (which also discards a pending detection). Commands that only read it (`/hs map export`) leave it. Room add and cut, door add and cut, `zones cut`, and `zones merge` use every box; a window needs the selection to be exactly one box; `/hs map export` takes the bounds of the whole selection. GameTest `MapToolGameTests.theSelectionLastsThroughCommandsUntilCancelled`, JUnit `SelectionTest`.
- **The wand says almost nothing.** Clicking corners no longer reports the first corner or the selection's size. When the second corner completes a selection that touches no room, it shows `/hs zones add <room|window|door>` as a reminder. A selection that touches a room shows nothing. Sneak-click still says "Selection cleared".
- **Adding is silent.** `/hs zones add`, `zones confirm`, and `zones cut` print nothing on success, since the outlines show the change. Refusals still explain themselves. A door add that skips blocks (in another door, or over the block limit) still says how many.
- **`/hs zones cut`** takes the selection out of every box of every room it touches (`BlockBox.minus` splits a box into up to six around the cut; `Zone.without`) and out of every door it touches (`DoorBlocks.cut`). `/hs zones cut room <room>` or `cut door <door>` cuts only that one. A room or door cut down to nothing is removed. A selection that touches nothing it can cut is refused. Windows aren't cut; remove them instead. JUnit `BlockBoxTest`, `ZoneTest`.
- **`/hs zones merge`.** With a Zone Wand selection, it merges the rooms the selection touches into the largest of them (by volume), and the doors it touches into the largest of them (by blocks). The merged room is a start room if any of the parts was; the merged door keeps the largest door's cost, power, and style. Without a selection, `/hs zones merge <from> <into>` merges two rooms or two doors by name. Names are looked up as rooms first, then as doors.
- **Undo and redo** (`tools/MapHistory`, `/hs undo`, `/hs redo`). Every `/hs zones` edit is recorded: add (including the room, door, or window it creates), cut, merge, remove, set, and detect then confirm. The history is per level, shared by everyone editing that map, up to 64 steps, and kept in memory only, so a server restart clears it. A new edit clears the redo steps. Each step records the map before and after, and undo puts back only the rooms, doors, windows, and features that step changed, so an untracked edit in between (a block broken out of a door, a machine placed) survives. A window add or remove also records the opening's blocks, so undo puts back the blocks that filled the hole, and redo the boards. Breaking or placing blocks by hand and `/hs map import` aren't recorded.
- **Room faces.** While the wand is held, `client/ZoneFaces` fills each nearby room's outer surface in its outline color at 25% opacity. It's drawn after translucent blocks, not culled, nudged toward the camera (`SeeThrough.decal`), and without writing depth, so it shows from inside a room on the walls and floor it lies against, and from outside where it lies on an outer surface. The surface is found block by block and cached per room geometry. Rooms over 65,536 blocks draw each box's six sides whole instead. Checked in game, not by tests. (It was oak planks tinted at 20% before.)
- **Ponder and Flywheel are gone** (2026-10-22). The outlines were the only thing they were used for, and they were 1.66 MB of the 3.89 MB jar. `client/outline/Outlines` draws the same outlines. JUnit `OutlinesTest`: a block has 12 edges, two side by side 16, a 2x1x2 square 20, the inside of an L keeps its edge, a line points from its start to its end in any direction, and the fade's curve.
- GameTest `MapToolGameTests`:
  - The wand picks the block looked at, and hints only for a finished selection that touches no room.
  - `zones add room`, `zones cut`, `zones add window` on a filled hole, and `zones add door` say nothing and do their edit. A missed cut says why. A room cut to nothing is gone.
  - A new door takes the cost given or the default, and adding to it keeps its price.
  - Undo and redo step through a named merge, a door add that made the door, and a window add with its blocks. An edit made in between is kept. A selection merge and a plain cut work and undo, and a new edit clears redo.
- JUnit `ZombiesMapPatchTest`: undoing a merge brings both rooms back and keeps what was added since.

**`/hs map paths`** lists each room connection and the cell the pathfinder uses for a normal 1x2 zombie: a doorway, or adjacent cells where rooms touch. It also reports connections with no walkable crossing, and pairs joined only by a closed door.

**Debug rules** (`/hs debug [rule [true|false]]`, per world, saved like gamerules; `/hs debug` lists them):
- `show_paths` (default false). While you hold a map tool, the server sends your client every game zombie's current path (red lines) and the doorway cells the pathfinder uses (purple boxes) every half second. With no game running, the doorways still show, computed from the map and cached until it changes.
- `show_errors` (default true). Send script errors to ops' chat.
- `no_zombies` (default false). Games spawn no zombies: `ForgeGameWorld.spawnZombie` refuses, and the game treats that as a spawn that didn't happen. A round's zombies never come, so it doesn't end until the rule is off again. Zombies already up stay.

**The wand's model** is your Blockbench model, a cube frame with a smaller frame turned inside it, drawn by GeckoLib (`geo/item/zone_wand.geo.json`, `textures/item/zone_wand.png`) so the inner frame keeps its free rotations. Both frames turn about the middle, looping every 10 seconds (`animations/item/zone_wand.animation.json`, `zone_wand_spin`, played by `ZoneWandItem`'s controller), wherever the wand is drawn. `client/BlockAlignedItemRenderer` puts the model's floor where a block model's would be, so `models/item/zone_wand.json` uses vanilla's block display transforms.

## As built (2026-10-24)

- **Low floors and soft doors** (`map/RoomDetector`, `tools/RoomDetection`). A floor cell is a block with a collision box no more than a block tall whose bottom face is full and top face isn't. Detection adds the floor layer under a room, never spreads through it, and starts a block up when the player stands in one. Door members stop the fill whatever they're made of, so a cobweb or vine door closes a room. Players and zombies are placed in the room at their feet or the cell above (`ZoneIndex.zoneOfBody`), used for discovery, a climbing zombie's "inside" and `/hs zones detect`'s "you're standing in a room" check, so maps detected before still work. Tests: `RoomDetectorTest`, `MapGameTests.detectionTakesCarpetsSlabsAndStairsButStopsAtACobwebDoor`, `GameTest` (discovering a room from a floor block).
- **Windows name the room behind them now.** `WindowDef` no longer stores a room; `/hs zones list` and the outline colour use `zoneBehind`, as gameplay does. An old save's `"zone"` key is ignored. `/hs zones add window` no longer fails with an unexpected error when the opening's corner has no room behind it.
- **`/hs zones set window <window> boards <0-6>`** changes a window's boards and re-boards it, with undo. Re-adding a window still keeps its boards, and says so, naming the `set` command.
- **Corners above, sneak for the block** (2026-10-24). A wand click puts the corner in the cell above the block looked at; sneaking, on the block itself (`ZoneWandItem.cellFor`, shared by the click and the client preview). The click goes through `onItemUseFirst`, which runs before the block's own use, so the wand never opens a door or flips a lever; clicks past the usual reach still come through `use`. `/hs zones desel` clears the selection (sneak-click did until now), and `/hsz` redirects to `/hs zones`. GameTest `MapToolGameTests.theWandSelectsWithoutUsingTheBlockAndDeselClearsIt`.
