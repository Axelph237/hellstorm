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

- **Reachability.** Every zone can be reached from a start zone through doors (ERROR).
- **Key features reachable.** The power switch and PaP are in reachable zones (ERROR if missing when a map has them).
- **Spawns.** Every zone has at least one spawn point, and each spawn point links to a window or is marked as an in-zone riser (WARNING).
- **Doors.** Every door links two distinct zones (ERROR).
- **Windows.** Each window opening is within the configured max size. Each mob profile that can spawn at a window fits through that window's opening (WARNING per window and profile).
- **Overlaps.** Zone boxes don't overlap other zones (ERROR).
- **Items.** `PunchAudit` results are merged in (see `pack-a-punch.md`): a punchable non-TacZ item with no `punch_id`, or a `punch_id` that doesn't resolve (ERROR).
- **Wall-buys.** Every TacZ gun id named by a wall-buy exists in the installed gun packs (ERROR).

Entry points:
- `/zombies map validate` prints the report.
- Game start runs it and refuses to start if `playable()` is false.
- While a map tool is held, problems with positions draw as red outlines (see `zone-tools-and-outlines.md`).

## Config surface

None. Validation rules are fixed. Thresholds come from `GameSettings` (window max size).

## Assets

None.

## Depends on

`map-model-and-zones.md`, `windows-and-barricades.md`, `pack-a-punch.md`.

## Verification

- JUnit, one test per check: build a small `CompiledMap` that breaks exactly that rule and assert exactly that problem code appears. Then fix it and assert it disappears.
- In game: a map with an unreachable zone refuses to start, and the chat message gives the zone's position.

## Phase

1, with checks added as later features land.
