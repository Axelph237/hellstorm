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

- **Layout core** (2026-10-31, `gen/layout`, pure). `Facing`, `Rotation` (quarter turns, put back at the corner: a turned
  room's sides swap and its north faces east), `Box` (blocks, both corners in), `Size`, `Connector` (opening, facing,
  `DOOR` or `OPEN`, group), `Piece` (a room as the layout sees it, with `problems()`: no connectors, or one that isn't one
  block thick on the side it faces), `Rules` with `TagRule`s (min, max, depth window), and `LayoutGenerator.generate`,
  which returns a `Layout` (rooms with their turn, corner and depth; joints, a door if either side is, marked when they
  close a loop) or why none could be made. Pieces are sorted by id first and each try draws from a `SplittableRandom`
  of the seed and the try number, so the order pieces come in never matters. 100 tries per seed. Nothing caps depth or
  distance. A start-tagged room never appears twice. A weight-0 room only goes in to meet a rule.
  - JUnit `LayoutGeneratorTest`: the same seed, the same layout, and twenty seeds at least sixteen different ones; the
    pieces' order doesn't matter; over 1,000 seeds every layout has 8 to 14 rooms, no two overlap, every joint's
    openings face each other side by side, every room is reached from the start, one start, the upgrader once at
    depth 3 or more, power once at depth 1 to 5, a max-1 room at most once, and some loops close; door versus open
    joints; groups and sizes; quarter turns keep openings on their sides; a bad connector or none is a problem;
    rules that can't be met are refused with a reason. The 1,000 seeds take half a second. Checked that turning off
    the overlap test fails it.
  - Not yet wired to anything: packs, authoring tools, placing in a world and the world types are the next steps.
