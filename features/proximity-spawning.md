# Proximity spawning

## Goal

Zombies spawn near the players, in the zones players are in and the zones next to them, never in locked or far-away parts of the map.

## Data shape

- `spawn_marker` block with a `SpawnMarker` BlockEntity:

```java
record SpawnMarkerConfig(
    ZoneId zone,
    SpawnKind kind,                    // WINDOW (outside a window) or RISER (inside the zone)
    Optional<BlockPos> window,         // WINDOW only: the WindowController this spawn feeds
    Set<ResourceLocation> profileFilter, // empty = any mob profile
    int weight) {}
```

## Implementation

`SpawnDirector` runs each tick during `RoundActive`:

1. **Active zones.** For each `Alive` player, take their current zone (from `ZoneIndex`) and every unlocked zone within `spawning.adjacent_hops` open-door hops of it.
2. **Candidates.** Spawn markers in active zones. A WINDOW marker is used only if its window still links to an unlocked zone.
3. **Weights.** Marker weight times a distance falloff to the nearest player: `1 / (1 + d / spawning.falloff_blocks)`. Markers closer than `spawning.min_distance` to any player are skipped.
4. **Pick and spawn.** If `alive < max_alive` and the spawn cooldown is 0, pick a weighted marker, pick a mob profile allowed there (see `mob-profiles-and-ai-injection.md`), and spawn. Decrement `toSpawn`.
5. **Emit** `zombie_spawned`.

If no candidate exists (all players in one zone with no markers), it falls back to the start zone's markers and logs a WARNING once per game.

## Config surface

- `spawning.adjacent_hops` (default 1).
- `spawning.falloff_blocks` (default 24).
- `spawning.min_distance` (default 6).
- Per marker: zone, kind, linked window, profile filter, weight.

## Assets

- Spawn marker block model uses a vanilla texture (for example `minecraft:block/spawner`). It is invisible in play mode.

## Depends on

`map-model-and-zones.md`, `game-loop-and-rounds.md`, `windows-and-barricades.md`, `mob-profiles-and-ai-injection.md`.

## Verification

- JUnit: with players in zone B on a chain A-B-C-D (all open), only B and its neighbors' markers are candidates at `adjacent_hops = 1`, and none from D.
- JUnit: locked zones never produce candidates.
- JUnit: weights fall off with distance and skip markers inside `min_distance`.
- In game: walk from the start zone through an opened door and see spawns follow.

## Phase

2.
