# Map validation

## Goal

A map that can't be played is caught before a game starts. Map makers see every problem with its position, both in chat and as red outlines in the world.

## Data shape

```java
enum Severity { ERROR, WARNING }
record Problem(Severity severity, String code, String message, Optional<BlockPos> pos) {}
record ValidationReport(List<Problem> problems) {
    boolean playable() { return problems.stream().noneMatch(p -> p.severity() == Severity.ERROR); }
}
```

## Implementation

`MapValidator.validate(ZombiesMap, ValidationContext)` is a pure function. The context carries the block probe, the window rules, each mob profile's hitbox, and the content problems `PunchAudit` and `ContentAudit` find (`GameManager.validationContext`). Checks:

- **Reachability.** Every room can be reached from a start room (ERROR). Rooms are joined when:
  - a walker two blocks tall, a player or a zombie, can walk from one room's walkable space to the other's, either straight across where their boxes touch, or in at most `map.max_passage_gap` steps (default 2) outside every room: a doorway, an arch, an open door, carpet. `Passages` searches on foot, using a `CellProbe` (`LevelCellProbe` classifies real blocks with `isPathfindable(LAND)`, and fences and wall blocks as `TALL`), so the validator itself stays pure.
  - A move is a step sideways, a jump up one block (with a third block of headroom), or a fall of up to 16 blocks (`Passages.MAX_FALL`, a drop a player at full health survives). Nothing stands on or jumps a `TALL` block. Open air alone joins nothing: rooms drawn taller than a wall two blocks high between them stay apart (2026-10-23; until then any open cell joined them, so the air above the wall made a door in it a loophole). A hole one block high is too low.
  - A room's walkable space is its standing spots (solid floor, two cells of headroom) in the largest group joined by steps of up to one block, plus what can be fallen to from there. The top of a wall drawn inside a room's box isn't in it. JUnit `PassagesTest`.
  - Not modelled: ladders, vines and other climbing, swimming, and jumping across gaps. A ladder over a wall is a way past a door the validator doesn't see.
  - Doors players buy (`/hs zones add door`) add edges from phase 5.
- **Closed doors between rooms** (WARNING at the door). Wooden doors always count as passages, because game zombies open them (`OpenDoorGoal`). A closed iron door, fence gate, or trapdoor is not a passage. When it is the only thing joining two rooms, the message says where it is and to open it, remove it, or make it a door players buy (`/hs zones add door`).
- **Key features reachable.** The power switch can be reached from a start room without a door that needs power (ERROR `power_switch_locked`), and the switch and the upgrader are in or beside a room (WARNING `feature_outside_room`).
- **Spawns.** Every room has at least one spawn marker inside it (WARNING). A marker outside every room is a WARNING, "not inside a room or linked to a window yet", until window links exist in phase 4. Zone-level problems carry no position: the message names the room.
- **Doors.** Every door links two distinct rooms (ERROR).
- **Loopholes** (`loophole`, WARNING; an ERROR that blocked games on 2026-10-23 only, and before that the `door_not_needed` WARNING). A door whose rooms are already joined directly by an opening that isn't a door unlocks nothing: a gap above or beside the door, a wall one block high (jumped) between rooms whose boxes touch, or a second doorway with no door. Each such pair of rooms gets "Loophole found between <room> and <room> at (x, y, z)", at the opening: the first cell where `Passages` found a mob crossing into the other room (`Passages.Result.openings`; for a doorway, the cell outside both rooms). Games still start: the door just unlocks nothing. A room also reachable the long way round, through a third room, isn't a loophole. JUnit `MapValidatorTest.badDoorsAreReported`.
- **Windows.** Each window opening is within the configured max size. Each mob profile that can spawn at a window fits through that window's opening (WARNING per window and profile).
- **Overlaps.** Room boxes don't overlap other rooms (ERROR).
- **Split start rooms.** A start room whose standing spots fall into pieces a player can't walk between (a box drawn past a wall) gets a WARNING pointing at each smaller piece. Players only spawn in the largest one.
- **Items** (WARNING). `PunchAudit` results are merged in (see `upgrader.md`): a punchable non-TacZ item with no `punch_id`, a `punch_id` that names nothing, and problems in the upgrade files. They're warnings, not errors: the upgrader refuses those items safely and hands them back.
- **Machines' settings** (WARNING `feature_config`). Every machine's settings still pass the checks its commands make, so a wall-buy whose gun pack was removed, or a crate whose pool isn't loaded, is reported.

Entry points:
- `/hs map validate` prints the report in the command error format (see `hud-and-prompts.md`): with errors, "Unable to validate map" and an ERROR line for each, then a WARNING line for each warning; with only warnings, "Map is valid, with warnings" and the WARNING lines. A problem's position is added to its line unless the message already names it. Messages mark their room, door, and window ids, numbers, positions, and commands (`util/Highlight`), which show in gold.
- Game start runs it and refuses to start if `playable()` is false.
- While a map tool is held, problems with positions draw as red outlines (see `zone-tools-and-outlines.md`).

## Config surface

None. Validation rules are fixed. Thresholds come from `GameSettings` (window max size).

## Assets

None.

## Depends on

`map-model-and-zones.md`, `windows-and-barricades.md`, `upgrader.md`.

## Verification

- JUnit, one test per check: build a small `CompiledMap` that breaks exactly that rule and assert exactly that problem code appears. Then fix it and assert it disappears.
- In game: a map with an unreachable room refuses to start, and the chat message gives the room's position.

## Phase

1, with checks added as later features land.

## As built

Codes and severities (`MapValidator`, `ContentAudit`, `PunchAudit.Kind`):
- ERROR, which stops a game opening or starting: `no_zones`, `empty_zone`, `no_start_zone`, `zone_overlap`, `unreachable_zone`, `window_too_large`, `window_unknown_zone`, `empty_door`, `door_not_between_zones`, `power_switch_locked`, and `no_mob_for_round` (a round no loaded mob profile can spawn in: every one there has weight 0, is outside its `min_round`/`max_round`, or is broken, so the round would never end).
- WARNING: `zone_without_spawn`, `spawn_outside_zone`, `closed_door_gap`, `window_without_spawn`, `mob_too_big_for_window`, `split_start_zone`, `loophole`, `feature_outside_room`, `mob_profile_broken` (its entity is missing, its nbt doesn't parse, or it isn't a mob), `wave_override_unspawnable`, `box_pool_empty`, `feature_config`, `bad_data_file` (a datapack file skipped at the last reload, from `DataFiles.errors()`), and the five `pap_*` codes.
Upgrade problems are warnings on purpose: the machine refuses those items safely. `ContentAudit` makes and throws away one mob per profile to check it. Validation runs when a lobby opens and again at `/hs game start`. In a game, a profile that's missing or failed to make its mob is never picked (`ForgeGameWorld.canSpawn`), and a round with nothing to spawn logs once (`Game.tickRound`). Tests: `MapValidatorTest` (power switch, upgrader), `MobRosterTest.firstEmptyRoundFindsRoundsNothingCanSpawnIn`, `ContentAuditTest`.
