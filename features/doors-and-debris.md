# Doors and debris

## Goal

Players spend points to open doors and clear debris, which unlocks new rooms. A door is any group of ordinary blocks the map maker selects with the Zone Wand, so a door can look like anything: a wooden door, a pile of gravel, a bookshelf wall. Opening it grows where zombies can path. Zombies spawn in a room only once a player has walked into it (see `proximity-spawning.md`).

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
- Select a box around the door's blocks, then `/hs zones add door <door> [cost]`. Run it again with the same door to add more boxes, and take blocks out with `/hs zones cut door <door>`.
- Doors show as Create-style `showCluster` outlines around their blocks while a map tool is held.
- `/hs zones set door <door> cost|power|style <value>`.
- Rooms are inferred from the rooms touching the group on each side.
- In edit mode, breaking a member removes it from the group. Removing the last member deletes the door.

**Snapshot and reset.**
- At game start, before anything can open, each door's current block states and BlockEntity NBT are saved as a `DoorSnapshot` in `MapSavedData`. This is persisted, so a crash mid-game can still be reset.
- `MapReset` restores every member from its snapshot. Running it twice gives the same result.

**Purchase.**
- `PlayerInteractEvent.RightClickBlock` on any member looks up the door in `DoorIndex` and goes through the common `Purchasable` path: reach, points, power, and that the door is closed.
- On success:
  1. Charge the cost.
  2. Set every member to air (no drops, no block updates that could break neighbors). DEBRIS plays falling-block particles from each block's own state. DOOR plays a door sound.
  3. Add both rooms to the game's unlocked set.
  4. Tell pathfinding to compute portal edges for the newly reachable rooms (see `zombie-pathfinding.md`).
  5. Emit `door_opened` (see `scripting-and-events.md`).
- **Prompts.** `DoorIndex` membership for nearby doors is synced to clients so the HUD can show "Door [750]" while looking at any member.

**Interaction with other systems.**
- Room detection (`zones detect`) treats door members as walls, so a closed door splits two rooms.
- Pathfinding treats the member positions as the portal. The passability grid for a room is built after the door opens, so the opening is walkable.
- Map export writes `DoorDef`s into `map.json`. The blocks themselves come along in the structure templates.

## Config surface

- Per door: cost, power requirement, style, name.
- `doors.default_cost` (750).
- At most 256 blocks per door (`DoorBlocks.MAX`). It's fixed in code, not a setting. `/hs zones add door` skips blocks past it and says how many.

## Assets

- None. Doors are made of whatever blocks the map maker built.

## Depends on

`map-model-and-zones.md`, `zone-tools-and-outlines.md`, `economy-and-points.md`, `power-switch.md`, `zombie-pathfinding.md`.

## Verification

- GameTest: a door made of mixed blocks (planks, a chest with items, a slab) opens fully when bought, unlocks both rooms, and charges exactly the cost. With too few points nothing changes.
- GameTest: reset restores every member exactly, including the chest's contents. Resetting twice changes nothing.
- GameTest: a power-gated door refuses until power is on.
- GameTest: a block can't be in two doors.
- In game: the wand's cluster outline matches the selected blocks, and clicking any member of the door shows the prompt and buys it.

## Phase

5.

## As built (phase 5)

- **`DoorDef`** (`map/`): id, member blocks, cost, `requires_power`, style `door | debris`. It lives in `ZombiesMap.doors()`, saved and synced with the map. The rooms aren't stored. `DoorDef.zones(index)` finds the rooms its blocks border, looking up to 2 cells out from each block, so editing rooms never leaves a door pointing at the wrong ones. There's no display name yet; prompts say "Door" or "Debris".
- **No Door Wand.** Doors are made from the Zone Wand selection (removed the Door Wand in phase 5). Every door shows as a gold cluster outline while a map tool is held.
- **Commands** (`/hs zones ... door`), each acting on the Zone Wand selection where it takes blocks. The selection clears afterwards:
  - `add door <id> [cost]` adds every non-air block in the selection to the door. If no door has that id, it makes one, at the given cost or `doors.default_cost`. If the door exists, the blocks join it and a cost given here is ignored, so doors that aren't one box are built by adding again. `/hs door create` is gone.
  - `cut door <id>` takes the selection's blocks out of a door. A plain `/hs zones cut` cuts every door (and room) the selection touches.
  - Blocks in another door are skipped, and a door holds at most 256 blocks (`tools/DoorBlocks`). `add` fails if nothing would be added.
  - `set door <id> cost <points> | power <bool> | style <door|debris>`.
  - `remove door <id>` (the blocks stay in the world).
  - `merge`, which joins the doors the selection touches, or `merge <from> <into>` by name (see `zone-tools-and-outlines.md`).
  - `/hs zones list` shows each door's rooms.
  - `/hs undo` takes back any of these, including a door that `add` created.
- **Edit mode.** Outside a game, breaking a door's block removes it from the door. Breaking the last block deletes the door.
- **Locked rooms.** A game unlocks rooms reachable from a start room through open passages only. `Game.unlocked()` replaces the fixed `GameMap.unlocked` for pathing. Spawning no longer reads it: a room spawns once a player has been in it (`proximity-spawning.md`, "As built").
- **Doors inside room boxes.** Rooms whose boxes touch connect only where a mob can step across: `Passages` reads the cell on each side of the shared face and links the rooms only when neither is solid, and only between cells in each room's walkable space (`Passages.walkableSpace`: the largest connected group of the room's non-solid cells). The walkable-space rule came from the Test map's barrel door, two blocks thick with air gaps in its near layer: the gaps belong to the far room's box and touch the near room, but the far layer seals them off, so they are pockets and no longer count. It applies to passages found through gaps outside every room as well. A door's blocks may sit inside the boxes, and the rooms stay apart until it opens. Before this, touching boxes always connected, so a room drawn up to a door was unlocked from the start and spawned zombies that couldn't get out. Offline validation (`MapValidator.validate(map)`) reads every cell inside a room as open and the rest as solid (`CellProbe.zonesOnly`).
- **A shut door is a wall, whatever it's made of.** `Passages.find` reads every block of a door not yet bought as solid, and `Doors.open` passes the doors bought so far. Before, passages took door blocks as the world has them, and the world reads a wooden door (zombies open those), a cobweb or a carpet as open, so the room behind such a door was unlocked from the start and its markers spawned zombies. Validation sees doors shut too, so the `loophole` error (see `map-validation.md`) only reports a real way around the door. JUnit `PassagesTest.aBoughtDoorOfPassableBlocksKeepsTheZonesApartUntilItIsOpen`; GameTest `aRoomNobodyHasEnteredSpawnsNothingEvenWithItsDoorBoughtUntilAPlayerWalksIn` (buying the wooden door unlocks the room).
- **Buying.** Right-click any block of a door, or press the use key on it (default O). Purchases go through `Shop` with the door's cost and power requirement, and an open door refuses.
- **Opening** (`game/Doors.open`):
  1. Set every block to air without drops or neighbour updates. Debris plays block-break particles, and doors play a door sound. The doorway is passable from this tick, while the blocks are still seen floating away (step 5), the way Call of Duty opens a door as it moves.
  2. Find passages in the world again; the opening is now a doorway.
  3. Unlock whatever became reachable through `Game.openDoor`, which emits `door_opened` with the door and the newly unlocked rooms.
  4. Rebuild zombie pathing over the bigger area with `GamePathing.reconnect`.
  5. The blocks float away (`game/DoorFlights`). Each non-air block gets a copy: a vanilla `block_display` entity tagged `hellstorm_door_flight`, standing where the block was and drawn exactly like it. Two ticks later (a client sent a display's start and end in the same tick draws only the end) each copy is given its end pose, a delay, and a 16-tick interpolation, and the client moves it by itself: it rises 1.5 blocks while shrinking about its own centre to nothing. That is one spawn and one data update per block, and nothing per frame. An Eye of Ender launch sound plays at the door's centre as they set off.
     - **Stagger.** The top layer goes first and each layer below follows one step later; along a layer the wave spreads from the door's middle out to its edges at half that speed (a block's step count is `layers below the top + horizontal distance from the middle / 2`). Top-first means a rising block never passes through one still waiting above it. A step is 3 ticks, shortened for big doors so the whole wave takes at most 24 ticks. A 1x2 doorway takes 24 ticks (1.2 seconds) from purchase to the last copy gone; the longest any door can take is 45 ticks (2 + 24 + 16, plus 3 ticks' grace for a client running behind).
     - **Cleanup.** The server removes each copy 3 ticks after its client should have shrunk it to nothing. `MapReset` discards every tagged copy in the level and forgets the flights, so ending a game mid-flight bricks the door back up with nothing left floating. A copy can still be saved with its chunk (the chunk unloaded mid-flight, or the server crashed); a tagged display loaded from disk is refused (`EntityJoinLevelEvent.loadedFromDisk`), so none ever comes back. Server stop ends every game first, which resets the map before the world saves.
     - **Late joiners.** A player who starts tracking a copy mid-flight sees it at its end pose, which is at or near nothing, until it's removed.
     - The display setters are private in vanilla; `META-INF/accesstransformer.cfg` opens the six the flights use.
- **Snapshot and reset.** `GameManager.register` saves every door block's state and block entity data to world saved data (`hellstorm_door_snapshots`). `MapReset` puts them back and forgets them, so a crash mid-game is fixed by the next game's creation.
- **Validation.**
  - ERROR for a door with no blocks.
  - ERROR for a door bordering fewer than two rooms.
  - WARNING for a door whose rooms are already connected without it.
  - Rooms reachable only through doors count as reachable.
- **Tests.**
  - JUnit: room inference and validation (`MapValidatorTest`), the codec (`ZombiesMapCodecTest`), and unlocking (`GameTest.openingADoorUnlocksTheZonesBeyondItOnce`).
  - GameTests (`DoorGameTests`): buying a door removes its blocks, charges, unlocks the room beyond, and extends pathing through the doorway; the reset bricks the doorway back up; a player who can't afford it gets nothing and pays nothing; a barrel door inside two touching rooms keeps the far one locked until bought. Flights: a bought door leaves a copy of each block (showing the right block) that's gone after the longest flight; a wall-wide door is partway gone mid-wave and fully gone within the bound; ending the game mid-flight bricks the door up and leaves no copies, then and after; a tagged copy loaded from disk is refused, while fresh copies and other displays aren't.
  - JUnit `DoorFlightsTest`: the stagger goes top first one step apart, spreads evenly from the middle of a layer, never starts a block before the one above it, and fits the biggest door (256 blocks) into 24 ticks.
  - JUnit `PassagesTest`: touching rooms connect over open cells and not over a door's blocks.
- **Doors and debris burst** (2026-10-23, `DoorFlights`). A bought door or debris no longer floats away in a wave: every block rises 1 block together, shrinking to 0.85, swells to 1.25 on the peak of the impact sound and shrinks to nothing, with the cha-ching and the impact (in place of the wooden door opening or gravel breaking). Debris still throws its break particles. The wave (`DoorFlights.stagger`) and its tests are gone. See `NEEDED/SOUNDS.md`.
