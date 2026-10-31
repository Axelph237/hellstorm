# Procedural generation

## Goal

Zombies maps generated from a seed, out of rooms map makers build and save, always playable, the same map every time for
the same seed and packs. Asked for on 2026-10-31:

1. A **workshop** world type for building rooms: a brick boilerplate of basic rooms and hallways, and the tools to save
   builds, chiefly a **Room Block** (a zombies-map structure block) that saves a room, hallway or other piece into a
   pack under a namespace.
2. Rooms and hallways carry **tags**. Several rooms can be tagged `pack_a_punch` while only one appears in a map.
3. A **generated map** world type whose creation screen picks which downloaded or saved packs to generate from.
4. Maps draw from a pool of any number of rooms, seamlessly.
5. Later: maps built into Minecraft terrain, or terrain generated round them.
6. Map makers set **densities**: which depths have Mystery Crates, how many perks appear, and so on.
7. **Placeholder blocks** for the upgrader, vending machines, wall-buys and the rest, placed in rooms and replaced at
   generation from the pack's configuration.
8. Packs carry **datapacks and scripts**, like a world folder with only rooms: no world data, no player data.
9. **Always playable.**
10. **No jigsaw limits** (vanilla jigsaw stops at 7 pieces deep and 128 blocks out).
11. **Seed repeatable.**

Decided with the user the same day: rooms join by **free-form connectors** (any room size, openings marked anywhere on
its walls, like jigsaw blocks), **one floor** for now (rooms connect sideways; a room can be any height inside), and
packs live in the game folder's **`hellstorm/maps/`**, beside today's map packs.

## Data shape

### A room

A room is a box of blocks (any size) plus what the game needs to know about it, all relative to its corner:

```java
record Connector(String id, Box opening, Facing facing, Kind kind, String group)  // kind: DOOR or OPEN
record RoomDef(String id, Size size, Set<String> tags, int weight, OptionalInt max, List<Connector> connectors,
               ZombiesMap slice)  // the room's zones, windows, spawn markers and doors, relative to its corner
```

- A **connector** is an opening on the room's outer face, 1 block thick, facing out. Two connectors join when their
  openings are the same size, face each other and sit side by side; the rooms then touch, wall to wall. `group` lets a
  pack say which openings fit which (a narrow `hall` opening only takes `hall` rooms); an empty group fits anything of
  the same size. `kind` says what the opening becomes when it's joined: `DOOR`, a door to buy (its cost from the
  pack's rules by depth), or `OPEN`, carved through. An opening nothing joins stays as the map maker built it, walled.
- Rooms turn by quarter turns about the vertical axis; their connectors, slice and placeholders turn with them.

### A pack (format 2)

A folder or zip in `hellstorm/maps/<pack>/`, like a world folder that holds only rooms:

```
pack.json            format 2, name, description, namespace
rooms/<id>.nbt       each room's blocks: a structure template, placeholders and connector markers included
rooms/<id>.json      its RoomDef: tags, weight, max, connectors, slice
generation.json      the rules: room count, the start tag, tag rules, densities, what placeholders become, door costs
datapack/...         data/<ns>/... : scripts, event defs, mob profiles, upgrades, crate pools
```

`generation.json`, for example:

```json
{
  "rooms": {"min": 10, "max": 16},
  "start": "start",
  "tags": [
    {"tag": "pack_a_punch", "min": 1, "max": 1, "depth": {"min": 4}},
    {"tag": "power", "min": 1, "max": 1, "depth": {"min": 2, "max": 5}}
  ],
  "features": {
    "mystery_box": {"count": {"min": 2, "max": 3}, "depth": {"min": 1, "max": 8}},
    "perk_machine": {"count": {"min": 4, "max": 4}, "pool": ["hellstorm:vigorade", "hellstorm:reload", "..."]},
    "wall_buy": {"pool": [{"gun": "tacz:glock_17", "depth": {"max": 2}}, {"gun": "tacz:ak47", "depth": {"min": 3}}]}
  },
  "doors": {"base_cost": 750, "cost_per_depth": 250, "block": "minecraft:oak_planks"}
}
```

**Depth** is a room's distance from the start room, counted in joined connectors.

### A layout

```java
record PlacedRoom(int index, String room, Rotation rotation, Pos origin, int depth)
record Joint(int roomA, String connectorA, int roomB, String connectorB, Kind kind)   // a joined pair
record Layout(List<PlacedRoom> rooms, List<Joint> joints)
```

## Implementation

1. **Layout** (`gen/layout`, pure, no Minecraft): grow rooms out from a start room by joining open connectors, in the
   spirit of jigsaw but with nothing capped. Each step picks an open connector, finds every room and quarter turn whose
   connector fits it, drops any that would overlap a placed room or break a tag rule, and picks one by weight. Tag
   rules that still need a room at this depth are favoured, and forced once the rooms left are as few as those owed.
   When nothing else fits, connectors that happen to face each other are joined too, closing loops. If the rules aren't
   met at the end, it tries again with the next sub-seed. A `SplittableRandom` from the seed and the packs, and lists
   kept in a fixed order, make it the same every time.
2. **Packs** (`gen/pack`): read and write format 2, folder or zip, with format 1's safety (allowed entries, no climbing
   paths, size caps).
3. **Authoring**: placeholder blocks (one per feature kind, with a facing), a connector marker block (kind and group in
   its settings), and the **Room Block**: select a room with it, name it and tag it on its screen, and it saves the
   blocks, the connectors read from the markers, and the room's slice of the map into a pack. Rooms are checked as
   they're saved: a room needs at least one connector, and every zone in it at least one spawn marker.
4. **Generating into a world**: turn the layout into blocks and a map. Each room's template is placed turned and moved
   (`StructureTemplate`, with its placement clipped to the chunk being built, so nothing is limited in size); joined
   openings are carved or filled with door blocks; placeholders become their machines from the pack's feature rules,
   picked with the same seed; each room's slice is turned, moved and merged into one `ZombiesMap`, with a door per
   `DOOR` joint linking the two rooms' zones. First through a command (`/hs map generate <pack...> [seed]`), so it can be
   tested and used in any world, then by the world types.
5. **World types**:
   - `hellstorm:zombies_map`: a void world whose chunk generator places the layout chunk by chunk as the world
     generates, with a creation screen (Forge's preset editor) to pick packs; on a dedicated server the packs go in
     `generator-settings`. The layout is saved with the world, so `/reload` or a changed pack never changes a world
     already made.
   - `hellstorm:map_workshop`: a flat world with a brick boilerplate of rooms and hallways near spawn, already marked
     with connectors and placeholders, and the tools given on joining.
6. **Datapacks and scripts**: a world made from packs installs each pack's datapack into its `datapacks/` and reloads,
   as `/hs map import` does today.
7. **Terrain, later**: the generator's base is pluggable. Void first; a later base hands chunks to a normal
   `NoiseBasedChunkGenerator` and places rooms into or on top of its terrain.

### Always playable

- By construction: every room is reached from the start (each joins one already placed), and tag rules (one upgrader,
  a power switch at a sensible depth) are met or the layout is retried.
- Each room is checked when saved, so every zone in a generated map has a spawn marker.
- The merged map then goes through `MapValidator`, as any map does before a game.

## Config surface

`generation.json` per pack; the world type's settings (packs, seed, base); the Room Block's screen.

## Assets

Placeholder blocks and the Room Block need textures: rows in `NEEDED/TEXTURES.md` when they're added; vanilla stand-ins
until then.

## Depends on

`map-model-and-zones.md`, `map-validation.md`, `map-export-import.md` (format 1 packs), `zone-tools-and-outlines.md`.

## Verification

- JUnit: the layout is the same for the same seed, different for others; over 1,000 seeds no rooms overlap, every joint
  lines up, every room is reached, tag rules hold, and the room count is in range or the failure is reported.
- JUnit: pack round trips and refused bad packs.
- GameTests: a pack generated into a world has its rooms where the layout says, carved openings, door groups, resolved
  placeholders and a map that validates.
- In game: build rooms in the workshop, save them, create a generated world from them, and play.

## As built

Everything below was built on 2026-10-31. Tests: JUnit `LayoutGeneratorTest`, `RoomTransformTest`, `PackTest`; GameTests
`GeneratedMapGameTests`.

### Decisions (and why)

- **Free-form connectors, one floor, packs in `hellstorm/maps/`**: the user's choices. Connectors over a grid so rooms can
  be any size; one floor to ship a working first version (`Facing` has four sides, and nothing in the layout assumes a
  height, so levels can come later).
- **Placed at first start, not chunk by chunk.** The design said to place pieces in `applyBiomeDecoration`, clipped to
  each chunk. Machines need their block entities set (what a wall-buy sells), which world generation's proto-chunks don't
  give a live block entity for. So a generated-map world is a void flat world, and the first time its server starts the
  whole map is placed into the live level, as `/hs map import` does (`GeneratedWorlds`). No jigsaw limit applies: the
  layout has no depth or distance cap, and placing isn't done by jigsaw at all.
- **The world's seed is the map's seed**, so the same seed and packs make the same map. `/hs map generate` takes a seed
  or picks one and says it.
- **Several packs pool their rooms; the first pack's rules rule.** A map maker can add a pack of extra rooms to another
  pack's rules. Rooms are known to the layout as `<pack>:<room>`.
- **No packs picked means every pack.** A dedicated server can't use the world-creation screen (`generator-settings` is
  only read for flat worlds), so an empty list there uses everything in `hellstorm/maps/`.
- **Each joined opening is two blocks thick**, one from each room, since rooms touch wall to wall. A door is both, made
  of the pack's door block; an open joint is both carved. An opening nothing joins is filled with the pack's seal block
  (bricks by default), so map makers build openings as connector blocks and never see them in a map.
- **A joint is a door if either side says door**, open only if both say open, so a hallway's open ends become doors where
  they meet a room.
- **Door cost rises with depth**: `base_cost + cost_per_depth x (depth - 1)` of the deeper room (750 and 250 by
  default). `power_from_depth` makes deeper doors need the power.
- **Placeholders without a `count` all become their machine**; with one, that many, chosen with the seed, at the
  allowed depths; too few placeholders for a rule's min, or no pool entry left for one, makes that layout unusable and
  the next is tried (up to 64), so a map never misses a required machine.
- **A wall-buy pool entry that isn't an item id is a TacZ gun id** (`tacz:ak47`), since guns aren't items of their own.
  Costs come from the entry, or the purchase defaults, or the machine's default.
- **The start room's zone is the only start zone**; every other room's zones aren't, whatever their slice says.
- **Rooms' ids in the map are prefixed** with their room number (`r3_hall`), zone ids cut to 32 characters with a hash.
- **The workshop is a void world with a stone platform**, the boilerplate rooms open at the top so a builder can see in.
  The start room's wall-buy is low on the wall: one at head height leaves a standing spot on top nobody can reach, which
  the validator warns about in a start room.
- **Saving checks the room**: it refuses a room with no connectors or a connector off its side, and warns about one with
  no zone or no spawn marker.

### What's there

- **Layout core** (`gen/layout`, pure). `Facing`, `Rotation` (quarter turns, put back at the corner: a turned room's
  sides swap and its north faces east), `Box`, `Size`, `Connector` (opening, facing, `DOOR` or `OPEN`, group), `Piece`
  (with `problems()`), `Rules` and `TagRule` (min, max, depth window), and `LayoutGenerator.generate`: a `Layout` (rooms
  with their turn, corner and depth; joints, marked when they close a loop) or why none could be made. Pieces sorted by id
  first, each try a `SplittableRandom` of the seed and try number, 100 tries. A start-tagged room never appears twice; a
  weight-0 room goes in only to meet a rule.
- **Packs** (`gen/pack`, format 2). `RoomPack` reads a folder or zip (`pack.json`, `generation.json`, `rooms/<id>.json` and
  `.nbt`, `datapack/...`), taking only those entries, no path out of the folder, at most 20,000 files and 512 MB;
  `writeRoom` saves a room, making the pack with default rules if it's new; `available` lists the format 2 packs.
  `RoomDef` (size, tags, weight, max, connectors, placeholders, map slice), `GenerationRules` (rooms, start, tag rules,
  `features` per placeholder kind with count, depths, pool and unique, `doors`, `seal`), `PlaceholderKind`
  (`wall_buy`, `vending_machine`, `mystery_box`, `upgrader`, `power_switch`), `FeatureResolver`.
- **Blocks** (`gen/block`, `registry/GenRegistries`, in the creative tab):
  - Five placeholders, `hellstorm:placeholder_<kind>`, facing whoever places them (the machine's front).
  - `hellstorm:connector`: fill an opening with them facing out of the room; touching ones facing the same way with the same
    settings are one opening. Its settings screen: what it becomes when joined (a door to buy, or open) and its group.
  - `hellstorm:room_block`: pack, room, tags, weight, at most, offset and size (x y z each); Save room saves (packet
    `SaveRoomPacket`: the settings, then `RoomSaver.save`), with the result as a notice. Its box is outlined
    (`client/gen/RoomBlockRenderer`).
- **Saving** (`gen/place/RoomSaver`): the box's blocks as a structure template, the connectors and placeholders read from
  the blocks, and the level map's slice (zones clipped to the box, windows, doors and features inside it), relative to
  the corner.
- **Generating** (`gen/place/MapGenerator`, `RoomTransform`): pool, lay out, decide placeholders, place each room's
  template turned (`RoomTransform.templatePosition`: Minecraft turns a block at x, z to -z, x without putting it back at
  the corner), carve, door or seal the openings, build the machines, merge the slices into the level's map (with the
  features the blocks registered themselves), put every window's boards up, play mode, install each pack's datapack into
  `datapacks/<pack>` and reload, and validate.
- **Command**: `/hs map generate <packs> [<seed>] [replace]`, op only, packs joined with `+` or `all`, the corner at the
  command's position; refused while a game runs or over a map without `replace`.
- **World types** (`gen/world`, `data/hellstorm/worldgen/world_preset/`): `hellstorm:zombies_map` ("Hellstorm Generated
  Map") and `hellstorm:map_workshop` ("Hellstorm Map Workshop"), both on the world-type list. Their overworld is
  `hellstorm:level` (`HellstormLevelSource`, a flat generator with `mode` and `packs`). The generated-map world's
  Customize button opens a pack picker (`client/gen/GeneratedMapPresetScreen`, through Forge's `RegisterPresetEditorsEvent`).
  On first start (`GeneratedWorlds`, remembered in the world's saved data) a map is generated at 0, 64, 0 from the
  world's seed, or the workshop is built there; the spawn goes in the start room or before the workshop, with
  `spawnRadius` 0. A workshop hands each player the kit (Room Block, connectors, placeholders, Zone Wand) the first time
  they join.
- **Workshop boilerplate** (`gen/world/Workshop`): `start` (11x11, four door openings, a wall-buy), `hall` (5x11, open at
  both ends, tagged `hallway`), `room` (11x11, four doors, a crate and a wall-buy), `perks` (11x11, two vending
  machines), `pack_a_punch` (9x9, the upgrader) and `power` (9x9, the switch, at most one), each with its zone, a spawn
  marker and a Room Block set to save it into the `workshop` pack, and a starter `generation.json` for that pack: 8 to 14
  rooms, one upgrader 3 or more rooms in, the power 1 to 4 in, at most two perk rooms, 2 to 4 vending machines with a
  different can each, 1 to 3 crate spots, every wall-buy with a gun by depth.
- **Tests.** The GameTest builds the workshop, saves its six rooms, and generates from the pack: a playable map, 8 to 14
  rooms, a zone per room, one start zone, one upgrader, one power switch, 2 to 4 vending machines, 1 to 3 crate spots,
  doors, a spawn marker per room, no placeholder or connector left, and the same layout again from the same seed. Another
  loads both world types from their data and checks their mode.

### Not yet

- Maps in Minecraft terrain (requirement 5): `HellstormLevelSource` is the place for it.
- More than one floor.
- Placing a map of any size is a single pass at first start; a very large pack would make that start slow.
