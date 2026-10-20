# Doors and debris

## Goal

Players spend points to open doors and clear debris, which unlocks new zones. A door is any group of ordinary blocks the map maker selects with the Zone Wand, so a door can look like anything: a wooden door, a pile of gravel, a bookshelf wall. Opening it grows where zombies spawn and where they can path.

## Data shape

Doors are not special blocks, so their data lives in `MapSavedData` instead of a BlockEntity:

```java
record DoorId(String value) {}

record DoorDef(
    DoorId id,
    String displayName,
    Set<BlockPos> members,          // any blocks, selected with the Zone Wand
    int cost,
    boolean requiresPower,
    DoorStyle style,                // DOOR or DEBRIS: only changes the open animation and sound
    ZoneId a, ZoneId b) {}

record DoorSnapshot(DoorId id, Map<BlockPos, BlockState> states, Map<BlockPos, CompoundTag> blockEntities) {}
```

`ZombiesMap` holds the `DoorDef`s. A `DoorPortal` (see `map-model-and-zones.md`) points at its `DoorId`. `MapSavedData` also holds a `DoorIndex` (member position to `DoorId`) so a click or a crosshair on any member finds its door.

## Implementation

**Making a door** (with the Zone Wand selection):
- Select a box around the door's blocks, then `/pap door create <door> [cost]`. Add more boxes with `/pap door add <door>` and take blocks out with `/pap door cut <door>`.
- Doors show as Create-style `showCluster` outlines around their blocks while a map tool is held.
- `/pap door set <door> cost|power|style <value>`.
- Zones are inferred from the zones touching the group on each side.
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
- **Prompts.** `DoorIndex` membership for nearby doors is synced to clients so the HUD can show "Door [750]" while looking at any member.

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

## As built (phase 5)

- **`DoorDef`** (`map/`): id, member blocks, cost, `requires_power`, style `door | debris`. It lives in `ZombiesMap.doors()`, saved and synced with the map. The zones aren't stored. `DoorDef.zones(index)` finds the zones its blocks border, looking up to 2 cells out from each block, so editing zones never leaves a door pointing at the wrong ones. There's no display name yet; prompts say "Door" or "Debris".
- **No Door Wand.** Doors are made from the Zone Wand selection (removed the Door Wand in phase 5). Every door shows as a gold cluster outline while a map tool is held.
- **Commands** (`/pap door`), each acting on the Zone Wand selection where it takes blocks. The selection clears afterwards:
  - `create <id> [cost]` makes a door from every non-air block in the selection, at `doors.default_cost` unless a cost is given.
  - `add <id>` adds the selection's blocks to a door, for doors that aren't one box.
  - `cut <id>` takes the selection's blocks out of a door.
  - Blocks in another door are skipped, and a door holds at most 256 blocks (`tools/DoorBlocks`). `create` and `add` fail if nothing would be added.
  - `set <id> cost <points> | power <bool> | style <door|debris>`.
  - `remove <id>` (the blocks stay in the world).
  - `list`, which shows each door's zones.
- **Edit mode.** Outside a game, breaking a door's block removes it from the door. Breaking the last block deletes the door.
- **Locked zones.** A game unlocks zones reachable from a start zone through open passages only. `Game.unlocked()` replaces the fixed `GameMap.unlocked` for spawning and pathing.
- **Buying.** Right-click any block of a door, or press the use key on it (default O). Purchases go through `Shop` with the door's cost and power requirement, and an open door refuses.
- **Opening** (`game/Doors.open`):
  1. Set every block to air without drops or neighbour updates. Debris plays block-break particles, and doors play a door sound.
  2. Find passages in the world again; the opening is now a doorway.
  3. Unlock whatever became reachable through `Game.openDoor`, which emits `door_opened` with the door and the newly unlocked zones.
  4. Rebuild zombie pathing over the bigger area with `GamePathing.reconnect`.
- **Snapshot and reset.** `GameManager.register` saves every door block's state and block entity data to world saved data (`packapunch_door_snapshots`). `MapReset` puts them back and forgets them, so a crash mid-game is fixed by the next game's creation.
- **Validation.**
  - ERROR for a door with no blocks.
  - ERROR for a door bordering fewer than two zones.
  - WARNING for a door whose zones are already connected without it.
  - Zones reachable only through doors count as reachable.
- **Tests.**
  - JUnit: zone inference and validation (`MapValidatorTest`), the codec (`ZombiesMapCodecTest`), and unlocking (`GameTest.openingADoorUnlocksTheZonesBeyondItOnce`).
  - GameTests (`DoorGameTests`): buying a door removes its blocks, charges, unlocks the zone beyond, and extends pathing through the doorway; the reset bricks the doorway back up; a player who can't afford it gets nothing and pays nothing.
