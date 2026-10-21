# Procedural generation (post-MVP)

## Goal

A world preset generates a whole zombies map from the seed: rooms, doors with rising costs, windows, wall-buys, perks, power, and PaP, always playable.

**Status:** deferred until after the MVP. The MVP keeps three things so this can be added without reworking anything:
- the generator-agnostic `ZombiesMap` model
- feature blocks that carry their config in NBT
- `MapValidator` as a pure function

## Data shape

```java
record RoomDef(ResourceLocation template, int cellsX, int cellsZ, List<Socket> sockets, Set<String> tags, int weight) {}
record Socket(int cellX, int cellZ, Direction side, SocketKind kind) {}   // DOOR or WINDOW
record Layout(List<PlacedRoom> rooms, List<PlacedDoor> doors) {}
```

Room pools and themes are datapack JSON. Room templates are NBT exported from hand-built maps.

## Implementation

- **World preset** `hellstorm:zombies_map` uses a custom `ZombiesChunkGenerator` (codec params: room count, theme, cell size).
- **`LayoutGenerator`** is pure and deterministic from the seed. Rooms snap to a grid of 16-block cells.
  1. Place the start room.
  2. Grow a critical path start → power → PaP.
  3. Branch side rooms weighted by depth and theme.
  4. Door costs rise with graph depth.
- **Validation.** Convert the layout to a `ZombiesMap` and run `MapValidator`. If it fails, retry with the next sub-seed.
- **Slots.** Marker blocks in templates become features: wall-buy slots draw from `WeaponTier` by depth, perk slots from the required perk set.
- **Placement.** In `applyBiomeDecoration`, place each piece that intersects the chunk, clipped to the chunk box (the same approach as vanilla `StructureStart.placeInChunk`). This avoids jigsaw's limits of depth 7 and 128-block radius. The layout is memoized per seed in a thread-safe way, and written to `MapSavedData` on first start, so `/reload` never changes an existing world.
- **Room authoring.** `/hs room export <name>` saves a selection of a hand-built map as a template plus a `RoomDef` with its footprint and sockets.

## Config surface

Generator codec params, room pool and theme JSON.

## Assets

Templates reuse vanilla blocks. No textures.

## Depends on

`map-model-and-zones.md`, `map-validation.md`, `zone-tools-and-outlines.md`.

## Verification

- JUnit: `LayoutGenerator` is deterministic per seed, and validator-clean over 1,000 seeds.
- In game: create a world with the preset and play a full game.

## Phase

Post-MVP.
