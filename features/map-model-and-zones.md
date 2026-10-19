# Map model and zones

## Goal

One data model describes a zombies map, whether a person built it with the tools or (later) a generator produced it. Spawning, pathfinding, validation, and the game loop read only this model.

## Data shape

```java
record ZoneId(String value) {}
record PortalId(UUID value) {}

record Zone(ZoneId id, String displayName, List<AABB> boxes, boolean start) {}

sealed interface Portal permits DoorPortal, WindowPortal {
    PortalId id();
    BlockPos anchor();
}
record DoorPortal(PortalId id, BlockPos anchor, DoorId door, ZoneId a, ZoneId b) implements Portal {}
record WindowPortal(PortalId id, BlockPos anchor, ZoneId inside) implements Portal {}

record FeatureRef(BlockPos pos, FeatureKind kind) {}   // WALL_BUY, WINDOW, BOX, PERK, PAP, POWER, TRAP, SPAWN

record ZombiesMap(
    String id,
    List<Zone> zones,
    List<Portal> portals,
    List<FeatureRef> features,
    List<DoorDef> doors,               // see doors-and-debris.md
    BlockPos origin,
    Optional<JsonObject> settingsOverride) {}
```

Config for a feature (gun id, cost, perk) lives in its BlockEntity, not in `ZombiesMap`. The map only indexes positions. Doors are the exception: they are groups of ordinary blocks with no BlockEntity, so their `DoorDef`s live in `ZombiesMap`. See "Feature blocks are the source of truth" in `PLAN.md`.

## Implementation

- **`MapSavedData`** stores the `ZombiesMap` per level via `level.getDataStorage().computeIfAbsent(...)`. It is written by the tools and read at game start.
- **Feature registration.** Each feature BlockEntity adds its `FeatureRef` in `onLoad` and when placed, and removes it when broken. The map therefore knows about features in unloaded chunks.
- **`ZoneIndex`** answers "which zone contains this position?" It buckets each zone box into the chunk sections it overlaps, so a lookup checks only the boxes in one section. It is rebuilt when zones change.
- **`MapCompiler`** turns `ZombiesMap` plus the loaded feature BlockEntities into an immutable `CompiledMap` at game start. It force-loads the map's chunks with `ForgeChunkManager` tickets for the length of the game.
- **Runtime room detection.** Every few ticks, `ZoneIndex` resolves each player's current zone. Spawning and pathfinding read this.
- **Reset.** `MapReset` walks the feature index and restores each default state (doors rebuilt from their snapshots, windows fully boarded, box at its start position, power off). It is idempotent. It runs at game end, and on server start if `MapSavedData.gameInProgress` is still set from a crash.

## Config surface

- `map.player_zone_refresh_ticks` (default 5).

## Assets

None.

## Depends on

`configuration.md`.

## Verification

- JUnit: `ZoneIndex` returns the right zone for points inside, on the edge of, and outside multi-box zones.
- JUnit: `ZombiesMap` codec round-trip.
- GameTest: open a door and break a window, run `MapReset` twice, and confirm every feature is back in its default state both times.

## Phase

1.
