# Zone tools and Create-style outlines

## Goal

Map makers draw zones, windows, and links with tools that feel like Create's super glue: boxes that follow the cursor, nearby zones outlined while a tool is held, and the hovered zone highlighted.

## Data shape

- Tool items: `zone_wand`, `door_wand`, `window_tool`, `link_tool`.
- **Selection is server-authoritative.** Clicks are handled on the server (`ToolState`), and each change is mirrored to the client (`SelectionPacket`). The client only draws it, plus the live corner under the cursor.
- **A corner is the air cell in front of the clicked face**, not the clicked block. Zones hold a room's open space, and two rooms that share a wall don't overlap.
- Server commands:
  - `/pap zone add <zone>` adds the current selection as a box. On an existing zone it adds another box, so a zone is the union of its boxes (L-shapes, corridors with side rooms).
  - `/pap zone merge <from> <into>` moves every box of one zone into another and deletes the first.
  - `/pap zone remove <zone> [box]`
  - `/pap zone detect <zone>`
  - `/pap zone start <zone>` marks a start zone.

## Implementation

**Outline library.** Create's outlines come from the Outliner in Catnip, which is compiled into the Ponder library (MIT, no Create needed).
- Dependency: `net.createmod.ponder:Ponder-Forge-1.20.1:1.0.92` from `https://maven.createmod.net`, plus Flywheel `[1.0.0,2.0)`, which Ponder requires on the client.
- Both are bundled with ForgeGradle jarJar. Version ranges include Create 6.0.8's pins (Ponder 1.0.91) so a pack with Create resolves to one copy.
- Ship Ponder's MIT notice.

**Boundary.** Only `client/outline/ZoneOutlines` imports `net.createmod.*`. Ponder ticks and renders the Outliner itself, so our code only calls `Outliner.getInstance().showAABB(slot, aabb)`, `showCluster(slot, positions)`, and `showLine(...)` each client tick. Outlines fade on their own about 8 ticks after we stop calling.

**Behavior** (modeled on Create's `SuperGlueSelectionHandler`):
- While any map tool is held, every zone within range draws as one Create-style cluster outline around the union of its boxes (`showCluster`, built once per geometry change and kept alive with `keep`). Zones over 65,536 blocks fall back to one outline per box. The color comes from a hash of the zone id, so it stays stable. Lines are 1/64 wide.
- The zone under the cursor (found by ray-clipping its boxes) gets 1/16 lines.
- First click sets a corner. Each tick the selection box snaps to the hovered block (`showAABB`, not `chaseAABB`). It turns yellow (`0xc5b548`) when out of reach or overlapping another zone.
- Door links draw as lines from the door to its two zones' nearest boxes. Spawn points and windows draw as small boxes.
- Validation problems with positions draw in red.

**Room detection** (`/pap zone detect <zone>`):
1. Flood-fill air from the player's position, stopping at solid blocks, door members, barricade blocks, and cells already in any zone, up to `tools.detect_max_volume`. Standing inside an existing zone is refused with a message naming that zone.
2. If the cap is hit, report "room is not closed" and stop.
3. Merge the voxel set into AABBs with greedy box merging (grow along x, then z, then y).
4. Preview the merged boxes and confirm with `/pap zone confirm` (or `/pap zone cancel`). The preview sends boxes rather than every cell, because a 32k-cell room is too large for one packet.

**Door Wand.** Picks arbitrary blocks into a door group, shown as a `showCluster` outline. Full behavior in `doors-and-debris.md`.

**Window Tool.** Drag a rectangle on a wall face. Confirming fills the opening with barricade blocks and places the controller (see `windows-and-barricades.md`). Openings over the max size are refused and shown in yellow.

**Link Tool.** Click a door member, then click zones to link. Links are inferred when a door sits between two zones, so this is mostly for overriding.

## Config surface

- `tools.outline_range` (default 64 blocks).
- `tools.detect_max_volume` (default 32768 blocks).
- `tools.reach` (default 32 blocks).

## Assets

- Tool item models use vanilla item textures (for example `minecraft:item/blaze_rod`, `minecraft:item/stick`, `minecraft:item/lead`).
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
