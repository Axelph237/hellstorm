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

- **Export** (`/hs map export <name>`):
  1. Run `MapValidator`. Warn on ERRORs but allow export with `--force`.
  2. Split the map's bounding box into 48³ pieces and save each with `StructureTemplate.fillFromWorld`. BlockEntity data (feature configs) comes along.
  3. Write `map.json` with positions relative to the map origin.
  4. Copy the map's datapack folder from the world.
- **Import** (`/hs map import <name> [pos]`):
  1. Install the datapack into the world's `datapacks/` and run `/reload`.
  2. Place each template with `placeInWorld`, offset to `pos`.
  3. Write `MapSavedData` from `map.json`, offset to `pos`.
  4. Run `MapValidator` and print the report.
- **Edit and play mode.** `/hs map mode edit|play`. Edit mode blocks games. Play mode allows games. Spawn markers hide while a game runs, in either case.
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

## As built (phase 7)

- **Commands** (ops, `command/MapPackCommands`). Packs live in `hellstorm/maps/` in the game folder (the server folder on a server). `/hs map packs` lists them.
  - `/hs map export <name> [force]` saves the Zone Wand selection if there is one, otherwise the map's bounds (`MapGeometry.bounds`: every zone box, feature, window, and door block) grown by `export.margin` (6) for walls, floors, and spawn areas outside the zones. It refuses while a game runs here, and when the map has validation errors unless `force`. The world's `datapacks/<name>` folder, if there is one, goes into the pack.
  - `/hs map import <name> [<pos>] [replace]` places the pack with its corner at `<pos>`, or where it was exported from. It refuses while a game runs, and over an existing map unless `replace`. The pack's datapack is written to `datapacks/<name>` (replacing that folder) and `/reload` runs. The validation verdict is printed.
- **Format** (`tools/MapPack`, format 1). A zip: `map.json` (`Manifest`: format, name, origin, size, and the `ZombiesMap` relative to the corner), `structure/<x>_<y>_<z>.nbt` (48-block pieces numbered from the corner, uncompressed structure templates without entities), and `datapack/...`. Block entities carry their settings and store no positions, so machines come back as they were. The pack is written to `<name>.zip.partial` and moved into place only when complete.
- **Reading a stranger's pack.** Only those three kinds of entry are allowed. A datapack path may contain only letters, digits, `_`, `.`, `-`, and `/`, and never `..`. At most 20,000 entries and 512 MB unpacked, counted while reading. A piece must lie inside the map's size, and a damaged piece or manifest is refused with a message rather than an error. Writing the datapack checks every path stays inside `datapacks/<name>`. Scripts still run in the Lua sandbox.
- **Placing.** Pieces go in with `StructureTemplate.placeInWorld` (client updates only, so nothing pops), into chunks that need not be loaded. The map is then moved to the new corner (`MapGeometry.moved`) and becomes the level's map, synced to players.
- **Edit and play mode** (`/hs map mode [edit|play]`, `map/MapMode`, `tools/MapModes`). Saved with the map in `MapSavedData` (worlds from before modes load as play, so their games keep working). Edit mode refuses to create a game (`GameManager.CreateResult.EditMode`; `/hs game join` explains how to switch). Switching refuses while a game runs. Importing a pack switches to play mode. Play mode used to hide every spawn marker too. Now the markers follow whether a game is running (see "As built (spawn markers hide during a game)" below), and switching mode puts them right, which shows markers an older world left hidden.
- **Not fighting, not hurt.** Found while playing an import: players in a game now take no damage in the lobby, the countdown, or after game over (`Game.protectsPlayers`). Before, lobby damage was vanilla (players could die) and countdown damage downed them before round 1.
- **Tests.**
  - JUnit `MapGeometryTest`: bounds cover every kind of position, and moving there and back is the same map.
  - JUnit `MapPackTest`: climbing paths, unknown entries, a missing manifest, another format, a piece outside the map, and a damaged piece are refused; a datapack is never written outside its folder.
  - GameTest `MapPackGameTests`: an exported area imported five blocks over has its blocks, the wall-buy's facing and settings, and the same map moved by five.
  - JUnit `DownsTest.playersAreProtectedOnlyWhileThereIsAFight`.
  - Checked on the Test map in a live client: exported (36x19x42, 80 KB), imported 500 blocks away with `replace`, validated as playable, and a game started there with only `start` and `hall` unlocked.

## As built (spawn markers hide during a game)

- **When.** A level's spawn markers are hidden from the moment its game registers (`GameManager.register`, so from the first `/hs game join` that opens the lobby) until it ends. Players never see where zombies come from; map makers see every marker between games, in play mode as well as edit mode. Hidden is the marker's existing `hidden` block state: drawn as nothing, with no outline to target.
- **How.** `SpawnMarkerBlock.setHidden(level, positions, hidden)` is the one switch. It only touches positions that hold a marker not already that way, so running it again changes nothing. `GameManager.register` hides the game map's markers. `MapReset.reset` shows them, so they come back when the game ends, and also before a new game is validated after a crash left them hidden. `GameManager.end` also shows the level map's markers, which covers one placed mid-game (it isn't in the game's map). A marker placed while a game runs hides itself (`onPlace`). `MapModes.set` sets hidden to whether a game runs.
- **Tests.** GameTest `MapModeGameTests`: `markersHideWhileAGameRunsAndShowWhenItEnds` (a game hides its markers, one placed mid-game hides itself, ending shows all three, a second reset leaves them showing) and `markersShowInBothModesAndEditModeBlocksGames` (a hidden marker is shown by a mode switch; edit mode refuses a game).
