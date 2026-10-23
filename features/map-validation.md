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

`MapValidator.validate(CompiledMap, Content, GameSettings)` is a pure function. Checks:

- **Reachability.** Every room can be reached from a start room (ERROR). Rooms are joined when:
  - their boxes share a face, or
  - a mob could walk between them through at most `map.max_passage_gap` cells (default 2) that belong to no room: a doorway, an arch, an open door, carpet. `Passages` finds these from the room surfaces using a `CellProbe` (`LevelCellProbe` classifies real blocks with `isPathfindable(LAND)`), so the validator itself stays pure.
  - Doors players buy (`/hs zones add door`) add edges from phase 5.
- **Closed doors between rooms** (WARNING at the door). Wooden doors always count as passages, because game zombies open them (`OpenDoorGoal`). A closed iron door, fence gate, or trapdoor is not a passage. When it is the only thing joining two rooms, the message says where it is and to open it, remove it, or make it a door players buy (`/hs zones add door`).
- **Key features reachable.** The power switch and the upgrader are in reachable rooms (ERROR if missing when a map has them).
- **Spawns.** Every room has at least one spawn marker inside it (WARNING). A marker outside every room is a WARNING, "not inside a room or linked to a window yet", until window links exist in phase 4. Zone-level problems carry no position: the message names the room.
- **Doors.** Every door links two distinct rooms (ERROR).
- **Loopholes** (`loophole`, ERROR, 2026-10-23; until then the `door_not_needed` WARNING). A door whose rooms are already joined directly by an opening that isn't a door unlocks nothing: a gap above or beside the door, rooms drawn taller than the wall between them, or a second doorway with no door. Each such pair of rooms gets "Loophole found between <room> and <room> at (x, y, z)", at the opening: the first cell where `Passages` found a mob crossing into the other room (`Passages.Result.openings`; for a doorway, the cell outside both rooms). It blocks a game from starting. A room also reachable the long way round, through a third room, isn't a loophole. JUnit `MapValidatorTest.badDoorsAreReported`.
- **Windows.** Each window opening is within the configured max size. Each mob profile that can spawn at a window fits through that window's opening (WARNING per window and profile).
- **Overlaps.** Room boxes don't overlap other rooms (ERROR).
- **Split start rooms.** A start room whose standing spots fall into pieces a player can't walk between (a box drawn past a wall) gets a WARNING pointing at each smaller piece. Players only spawn in the largest one.
- **Items.** `PunchAudit` results are merged in (see `upgrader.md`): a punchable non-TacZ item with no `punch_id`, or a `punch_id` that doesn't resolve (ERROR).
- **Wall-buys.** Every TacZ gun id named by a wall-buy exists in the installed gun packs (ERROR).

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
