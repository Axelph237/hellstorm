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

**Outline library.** Create's outlines come from the Outliner in Catnip, which is compiled into the Ponder library (MIT, no Create needed).
- Dependency: `net.createmod.ponder:Ponder-Forge-1.20.1:1.0.92` from `https://maven.createmod.net`, plus Flywheel `[1.0.0,2.0)`, which Ponder requires on the client.
- Both are bundled with ForgeGradle jarJar. Version ranges include Create 6.0.8's pins so a pack with Create resolves to one copy.
- Ship Ponder's MIT notice.

**Boundary.** Only `client/outline/ZoneOutlines` imports `net.createmod.*`. Ponder ticks and renders the Outliner itself, so our code only calls `showAABB`, `showCluster`, and `showLine` each client tick. Outlines fade on their own about 8 ticks after we stop calling.

**Behavior** (modeled on Create's `SuperGlueSelectionHandler`):
- While the wand is held, every room within range draws as one cluster outline around the union of its boxes (built once per geometry change and kept alive with `keep`). Rooms over 65,536 blocks fall back to one outline per box. The color comes from a hash of the room id, so it stays stable. Lines are 1/64 wide.
- The room under the cursor (found by ray-clipping its boxes) gets 1/16 lines.
- First click sets a corner. Each tick the selection box snaps to the hovered block. It turns yellow (`0xc5b548`) when out of reach or overlapping another room.
- Doors draw as gold cluster outlines, windows as boxes in their room's color, spawn markers as small boxes with a line to their window.
- Validation problems with positions draw in red.

**Room detection** (`/hs zones detect <room>`):
1. Flood-fill air from the player's position, stopping at solid blocks, door members, barricade blocks, and cells already in any room, up to `tools.detect_max_volume`. Standing inside an existing room is refused with a message naming that room.
2. If the cap is hit, report "room is not closed" and stop.
3. Merge the voxel set into AABBs with greedy box merging (grow along x, then z, then y).
4. Preview the merged boxes and confirm with `/hs zones confirm` (or `/hs zones cancel`). The preview sends boxes rather than every cell, because a 32k-cell room is too large for one packet.

## Config surface

- `outline_range` (client, default 64 blocks).
- `tools.detect_max_volume` (default 32768 blocks).
- `tools.reach` (default 32 blocks).

## Assets

- Room faces use vanilla's `minecraft:textures/block/oak_planks.png`, tinted.
- **Needed from you:** a face texture for the hovered room highlight. Until then, hover shows thicker lines only. Listed in `TEXTURES_NEEDED.md`.

## Depends on

`map-model-and-zones.md`, `map-validation.md`.

## Verification

- JUnit: greedy box merging covers exactly the input voxels with no overlaps, on an L-shaped room and a room with a pillar.
- JUnit: flood fill stops at the volume cap on an open map.
- In game: holding the wand shows both test rooms in distinct colors, hovering thickens one, and the selection box follows the cursor and turns yellow on overlap.
- Launch with Create 6.0.8 installed: one copy of Ponder loads and outlines still draw.

## Phase

1.

## As built

- **A corner is the block you're looking at** (`ZoneWandItem.targetCell`). It used to be the air cell in front of the clicked face; looking at a door's or window's own blocks is easier. Clicking a room's floor and ceiling takes them into the room. Rooms can't overlap, so two rooms sharing a wall can't both take it in. To select a window's hole, fill it with any blocks first. `/hs zones add window` replaces them with boards.
- **The wand says almost nothing.** Clicking corners no longer reports the first corner or the selection's size. When the second corner completes a selection that touches no room, it shows `/hs zones add <room|window|door>` as a reminder. A selection that touches a room shows nothing. Sneak-click still says "Selection cleared".
- **Adding is silent.** `/hs zones add`, `zones confirm`, and `zones cut` print nothing on success, since the outlines show the change. Refusals still explain themselves. A door add that skips blocks (in another door, or over the block limit) still says how many.
- **`/hs zones cut`** takes the selection out of every box of every room it touches (`BlockBox.minus` splits a box into up to six around the cut; `Zone.without`) and out of every door it touches (`DoorBlocks.cut`). `/hs zones cut room <room>` or `cut door <door>` cuts only that one. A room or door cut down to nothing is removed. A selection that touches nothing it can cut is refused. Windows aren't cut; remove them instead. JUnit `BlockBoxTest`, `ZoneTest`.
- **`/hs zones merge`.** With a Zone Wand selection, it merges the rooms the selection touches into the largest of them (by volume), and the doors it touches into the largest of them (by blocks). The merged room is a start room if any of the parts was; the merged door keeps the largest door's cost, power, and style. Without a selection, `/hs zones merge <from> <into>` merges two rooms or two doors by name. Names are looked up as rooms first, then as doors.
- **Undo and redo** (`tools/MapHistory`, `/hs undo`, `/hs redo`). Every `/hs zones` edit is recorded: add (including the room, door, or window it creates), cut, merge, remove, set, and detect then confirm. The history is per level, shared by everyone editing that map, up to 64 steps, and kept in memory only, so a server restart clears it. A new edit clears the redo steps. Each step records the map before and after, and undo puts back only the rooms, doors, windows, and features that step changed, so an untracked edit in between (a block broken out of a door, a machine placed) survives. A window add or remove also records the opening's blocks, so undo puts back the blocks that filled the hole, and redo the boards. Breaking or placing blocks by hand and `/hs map import` aren't recorded.
- **Zone faces.** While the wand is held, `client/ZonePlanks` draws each nearby room's outer surface in oak planks tinted the room's color at 20% opacity, one plank tile per block. It's drawn after translucent blocks, not culled, nudged toward the camera (`SeeThrough.decal`), and without writing depth, so it shows from inside a room on the walls and floor it lies against, and from outside where it lies on an outer surface. Ponder's own face textures are culled from inside and offset into the wall, which hides them in a room. The surface is found block by block and cached per room geometry. Rooms over 65,536 blocks draw each box's six sides whole instead. Checked in game, not by tests.
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
