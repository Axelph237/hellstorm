# Zone tools and Create-style outlines

## Goal

Map makers draw zones, windows, and doors with a tool that feels like Create's super glue: a box that follows the cursor, nearby zones outlined while the tool is held, and the hovered zone highlighted.

## Data shape

- One tool item, the Zone Wand (`zone_wand`). Doors and windows are made from its selection (see `doors-and-debris.md` and `windows-and-barricades.md`).
- **Selection is server-authoritative.** Clicks are handled on the server (`ToolState`), and each change is mirrored to the client (`SelectionPacket`). The client only draws it, plus the live corner under the cursor.
- Zone commands:
  - `/hs zone add <zone>` adds the selection as a box. On an existing zone it adds another box, so a zone is the union of its boxes (L-shapes, corridors with side rooms).
  - `/hs zone cut <zone>` takes the selection out of a zone.
  - `/hs zone merge <from> <into>` moves every box of one zone into another and deletes the first.
  - `/hs zone remove <zone>`, `/hs zone list`, `/hs zone start <zone> <bool>`.
  - `/hs zone detect <zone>`, then `/hs zone confirm` or `/hs zone cancel`.

## Implementation

**Outline library.** Create's outlines come from the Outliner in Catnip, which is compiled into the Ponder library (MIT, no Create needed).
- Dependency: `net.createmod.ponder:Ponder-Forge-1.20.1:1.0.92` from `https://maven.createmod.net`, plus Flywheel `[1.0.0,2.0)`, which Ponder requires on the client.
- Both are bundled with ForgeGradle jarJar. Version ranges include Create 6.0.8's pins so a pack with Create resolves to one copy.
- Ship Ponder's MIT notice.

**Boundary.** Only `client/outline/ZoneOutlines` imports `net.createmod.*`. Ponder ticks and renders the Outliner itself, so our code only calls `showAABB`, `showCluster`, and `showLine` each client tick. Outlines fade on their own about 8 ticks after we stop calling.

**Behavior** (modeled on Create's `SuperGlueSelectionHandler`):
- While the wand is held, every zone within range draws as one cluster outline around the union of its boxes (built once per geometry change and kept alive with `keep`). Zones over 65,536 blocks fall back to one outline per box. The color comes from a hash of the zone id, so it stays stable. Lines are 1/64 wide.
- The zone under the cursor (found by ray-clipping its boxes) gets 1/16 lines.
- First click sets a corner. Each tick the selection box snaps to the hovered block. It turns yellow (`0xc5b548`) when out of reach or overlapping another zone.
- Doors draw as gold cluster outlines, windows as boxes in their zone's color, spawn markers as small boxes with a line to their window.
- Validation problems with positions draw in red.

**Room detection** (`/hs zone detect <zone>`):
1. Flood-fill air from the player's position, stopping at solid blocks, door members, barricade blocks, and cells already in any zone, up to `tools.detect_max_volume`. Standing inside an existing zone is refused with a message naming that zone.
2. If the cap is hit, report "room is not closed" and stop.
3. Merge the voxel set into AABBs with greedy box merging (grow along x, then z, then y).
4. Preview the merged boxes and confirm with `/hs zone confirm` (or `/hs zone cancel`). The preview sends boxes rather than every cell, because a 32k-cell room is too large for one packet.

## Config surface

- `outline_range` (client, default 64 blocks).
- `tools.detect_max_volume` (default 32768 blocks).
- `tools.reach` (default 32 blocks).

## Assets

- Zone faces use vanilla's `minecraft:textures/block/oak_planks.png`, tinted.
- **Needed from you:** a face texture for the hovered zone highlight. Until then, hover shows thicker lines only. Listed in `TEXTURES_NEEDED.md`.

## Depends on

`map-model-and-zones.md`, `map-validation.md`.

## Verification

- JUnit: greedy box merging covers exactly the input voxels with no overlaps, on an L-shaped room and a room with a pillar.
- JUnit: flood fill stops at the volume cap on an open map.
- In game: holding the wand shows both test zones in distinct colors, hovering thickens one, and the selection box follows the cursor and turns yellow on overlap.
- Launch with Create 6.0.8 installed: one copy of Ponder loads and outlines still draw.

## Phase

1.

## As built

- **A corner is the block you're looking at** (`ZoneWandItem.targetCell`). It used to be the air cell in front of the clicked face; looking at a door's or window's own blocks is easier. Clicking a room's floor and ceiling takes them into the zone. Zones can't overlap, so two rooms sharing a wall can't both take it in. To select a window's hole, fill it with any blocks first: `/hs window add` replaces them with boards.
- **Adding is silent.** `/hs zone add`, `zone confirm`, `zone cut`, `window add`, `door create`, and `door add` print nothing on success, since the outlines show the change. Refusals still explain themselves. A door add that skips blocks (in another door, or over the block limit) still says how many.
- **`/hs zone cut <zone>`** takes the selection out of every box of the zone (`BlockBox.minus` splits a box into up to six around the cut; `Zone.without`). A zone cut down to nothing is removed. A selection that doesn't touch the zone is refused. JUnit `BlockBoxTest`, `ZoneTest`.
- **Zone faces.** While the wand is held, `client/ZonePlanks` draws each nearby zone's outer surface in oak planks tinted the zone's color at 20% opacity, one plank tile per block. It's drawn after translucent blocks, not culled, nudged toward the camera (`SeeThrough.decal`), and without writing depth, so it shows from inside a room on the walls and floor it lies against, and from outside where it lies on an outer surface. Ponder's own face textures are culled from inside and offset into the wall, which hides them in a room. The surface is found block by block and cached per zone geometry. Zones over 65,536 blocks draw each box's six sides whole instead. Checked in game, not by tests.
- GameTest `MapToolGameTests`: the wand picks the block looked at, zone add, zone cut, window add on a filled hole, and door create say nothing and do their edit, a missed cut says why, and a zone cut to nothing is gone.

**`/hs map paths`** lists each zone connection and the cell the pathfinder uses for a normal 1x2 zombie: a doorway, or adjacent cells where zones touch. It also reports connections with no walkable crossing, and pairs joined only by a closed door.

**Debug rules** (`/hs debug [rule [true|false]]`, per world, saved like gamerules; `/hs debug` lists them):
- `show_paths` (default false). While you hold a map tool, the server sends your client every game zombie's current path (red lines) and the doorway cells the pathfinder uses (purple boxes) every half second. With no game running, the doorways still show, computed from the map and cached until it changes.
- `show_errors` (default true). Send script errors to ops' chat.
- `no_zombies` (default false). Games spawn no zombies: `ForgeGameWorld.spawnZombie` refuses, and the game treats that as a spawn that didn't happen. A round's zombies never come, so it doesn't end until the rule is off again. Zombies already up stay.

**The wand's model** is your Blockbench model, a cube frame with a smaller frame turned inside it, drawn by GeckoLib (`geo/item/zone_wand.geo.json`, `textures/item/zone_wand.png`) so the inner frame keeps its free rotations. Both frames turn about the middle, looping every 10 seconds (`animations/item/zone_wand.animation.json`, `zone_wand_spin`, played by `ZoneWandItem`'s controller), wherever the wand is drawn. `client/BlockAlignedItemRenderer` puts the model's floor where a block model's would be, so `models/item/zone_wand.json` uses vanilla's block display transforms.
