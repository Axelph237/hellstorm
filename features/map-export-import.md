# Map export and import

## Goal

A finished map can be shared as one file and loaded into another world, with its blocks, zones, feature configs, settings, and scripts intact.

## Data shape

A map pack is a zip:

```
mymap.zip
  map.json                  ZombiesMap (zones, portals, doors, feature index, settings override)
  structure/*.nbt           the map's blocks as one or more structure templates
  datapack/                 the map's datapack: EventDefs, Lua scripts, mob profiles, punch defs, ...
  pack.mcmeta
```

## Implementation

- **Export** (`/pap map export <name>`):
  1. Run `MapValidator`. Warn on ERRORs but allow export with `--force`.
  2. Split the map's bounding box into 48³ pieces and save each with `StructureTemplate.fillFromWorld`. BlockEntity data (feature configs) comes along.
  3. Write `map.json` with positions relative to the map origin.
  4. Copy the map's datapack folder from the world.
- **Import** (`/pap map import <name> [pos]`):
  1. Install the datapack into the world's `datapacks/` and run `/reload`.
  2. Place each template with `placeInWorld`, offset to `pos`.
  3. Write `MapSavedData` from `map.json`, offset to `pos`.
  4. Run `MapValidator` and print the report.
- **Edit and play mode.** `/pap map mode edit|play`. Edit mode shows markers and blocks games. Play mode hides markers and allows games.
- **Safety.** Imported scripts run inside the Lua sandbox (see `scripting-and-events.md`), so a downloaded map can't touch the host machine.

## Config surface

- `export.piece_size` (48).

## Assets

None.

## Depends on

`map-model-and-zones.md`, `map-validation.md`, `scripting-and-events.md`.

## Verification

- Round trip: export the 4-zone test map, import it into a fresh void world at another position, and confirm `MapValidator` passes, every feature config matches, and a full game can be played.
- JUnit: `map.json` offset math round-trips positions.

## Phase

7.
