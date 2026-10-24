# Known issues

Each item was checked against the code on 2026-10-24, several with a probe test; few have been seen in game. Items found to be false or intended were removed. Remove an item when it's fixed or decided against.

## Gameplay and commands

- **Crowds jam a 1-wide doorway for a few seconds.** Two zombies side by side at its mouth both press into the jambs and neither turns in, and sometimes nobody gets through for 2 to 3 seconds before it clears (about 8% of runs of the crowd GameTest, which now checks that the doorway keeps flowing).

## Not built yet

- **Per-map setting overrides.** `SettingsResolver` exists and is tested, but `GameManager.settings()` always passes no override, and maps and map packs store none. The feature docs say so.
- `power.start_on`, powered machine blockstates, `wallbuys.default_pap_ammo_cost`, `windows.default_boards`, and `doors.max_members` aren't settings.
