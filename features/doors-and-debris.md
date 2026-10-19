# Doors and debris

## Goal

Players spend points to open doors and clear debris, which unlocks new zones. A door is any group of ordinary blocks the map maker picks with the Door Wand, so a door can look like anything: a wooden door, a pile of gravel, a bookshelf wall. Opening it grows where zombies spawn and where they can path.

## Data shape

Doors are not special blocks, so their data lives in `MapSavedData` instead of a BlockEntity:

```java
record DoorId(String value) {}

record DoorDef(
    DoorId id,
    String displayName,
    Set<BlockPos> members,          // any blocks, picked with the Door Wand
    int cost,
    boolean requiresPower,
    DoorStyle style,                // DOOR or DEBRIS: only changes the open animation and sound
    ZoneId a, ZoneId b) {}

record DoorSnapshot(DoorId id, Map<BlockPos, BlockState> states, Map<BlockPos, CompoundTag> blockEntities) {}
```

`ZombiesMap` holds the `DoorDef`s. A `DoorPortal` (see `map-model-and-zones.md`) points at its `DoorId`. `MapSavedData` also holds a `DoorIndex` (member position to `DoorId`) so a click or a crosshair on any member finds its door.

## Implementation

**Door Wand** (a map-maker tool, same feel as the Zone Wand):
- `/zombies door create <door>` starts a new group, or right-click a member of an existing door to select it.
- Right-click a block to add or remove it. Click two corners to add every non-air block in the box, like the Zone Wand selection.
- The selected group shows as a Create-style `showCluster` outline around the blocks. Other doors in range show thinner clusters in their own color. A block already in another door shows yellow and is refused.
- `/zombies door set <door> cost|power|style|name <value>`, or the creative config screen opened by sneak-right-clicking a member with the wand.
- Zones are inferred from the zones touching the group on each side. The Link Tool overrides them.
- In edit mode, breaking a member removes it from the group. Removing the last member deletes the door.

**Snapshot and reset.**
- At game start, before anything can open, each door's current block states and BlockEntity NBT are saved as a `DoorSnapshot` in `MapSavedData`. This is persisted, so a crash mid-game can still be reset.
- `MapReset` restores every member from its snapshot. Running it twice gives the same result.

**Purchase.**
- `PlayerInteractEvent.RightClickBlock` on any member looks up the door in `DoorIndex` and goes through the common `Purchasable` path: reach, points, power, and that the door is closed.
- On success:
  1. Charge the cost.
  2. Set every member to air (no drops, no block updates that could break neighbors). DEBRIS plays falling-block particles from each block's own state. DOOR plays a door sound.
  3. Add both zones to the game's unlocked set.
  4. Tell pathfinding to compute portal edges for the newly reachable zones (see `zombie-pathfinding.md`).
  5. Emit `door_opened` (see `scripting-and-events.md`).
- **Prompts.** `DoorIndex` membership for nearby doors is synced to clients so the HUD can show "Press [Use] to open Door [750]" while looking at any member.

**Interaction with other systems.**
- Room detection (`zone detect`) treats door members as walls, so a closed door splits two zones.
- Pathfinding treats the member positions as the portal. The passability grid for a zone is built after the door opens, so the opening is walkable.
- Map export writes `DoorDef`s into `map.json`. The blocks themselves come along in the structure templates.

## Config surface

- Per door: cost, power requirement, style, name.
- `doors.default_cost` (750).
- `doors.max_members` (256).

## Assets

- None. Doors are made of whatever blocks the map maker built.

## Depends on

`map-model-and-zones.md`, `zone-tools-and-outlines.md`, `economy-and-points.md`, `power-switch.md`, `zombie-pathfinding.md`.

## Verification

- GameTest: a door made of mixed blocks (planks, a chest with items, a slab) opens fully when bought, unlocks both zones, and charges exactly the cost. With too few points nothing changes.
- GameTest: reset restores every member exactly, including the chest's contents. Resetting twice changes nothing.
- GameTest: a power-gated door refuses until power is on.
- GameTest: a block can't be in two doors.
- In game: the wand's cluster outline matches the selected blocks, and clicking any member of the door shows the prompt and buys it.

## Phase

5.
