# Known issues

Each item was checked against the code on 2026-10-24, several with a probe test; few have been seen in game. Items found to be false or intended were removed. Remove an item when it's fixed or decided against.

## Gameplay and commands

- **A round stalls forever when nothing can spawn.** If every mob profile allowed in a round has weight 0 (or `min_round`/`max_round` leave a round with none), or a wave event's `mob_override` names only missing or broken profiles, no zombie spawns and the round never ends. `ForgeGameWorld.canSpawn` says yes to a profile that doesn't exist, and a missing profile fails to spawn without a log line. Nothing in validation catches either case.
- **Trap times over 72000 ticks wipe the trap's settings.** `/hs trap active` and `/hs trap cooldown` accept any value and say it worked, but the trap's codec caps both at 72000. The block entity then fails to save its whole config without a message, so after a reload the trap is back to all defaults (name, cost, power, signal), and a map pack exported meanwhile carries the defaults.
- **Wall-buy refills charge for a full gun.** Buying ammo for a gun that's already full, or that has unlimited ammo, costs the ammo price and changes nothing (`WallBuy.decide` refills whenever the gun is owned). A refill on a gun with no `DummyAmmo` tag also turns it into an empty dummy-ammo gun.
- **A zombie can spawn frozen in a chunk that isn't ticking entities.** `ForgeGameWorld.spawnZombie` only checks `level.isLoaded`, so a zombie added to a chunk before it's fully promoted is stored but hidden, or one beyond the simulation distance never moves, and either holds the round open. Seen as the flaky "hellhound spawned null" GameTest.
- **Crowds jam a 1-wide doorway for a few seconds.** Two zombies side by side at its mouth both press into the jambs and neither turns in; in about 8% of runs of the crowd GameTest nobody gets through for 2 to 3 seconds before it clears.
- **Room detection:** a Hellstorm door made of blocks with no collision box (cobwebs, vines) doesn't stop the fill, though `features/doors-and-debris.md` says door members are walls. Carpets, slabs, stairs, paths, farmland and 2 or more snow layers are left out of detected rooms, and starting on one gives "Your feet are inside a solid block". Since players are placed in the room at their feet, a room whose floor is all such blocks is never discovered, so its spawns never wake.
- **`/hs zones add window` can fail with "An unexpected error occurred".** It finds the room behind the window from the opening's corner column (`orElseThrow`) after checking only the centre column, so an opening whose corner has no room behind it throws.
- **A window's board count can't be changed.** Re-adding a window keeps its old count without saying so, and there's no `/hs zones set window`.
- **`/hs map import <name> replace` leaves the old map's blocks** (barricades, machines) in the world, and a leftover wall-buy, crate or perk machine still sells in games, since `FeatureBlock.use` doesn't check the game's map.
- **Map edits while a lobby is open don't reach the game.** Validation, the map reset and the game's map snapshot happen at the first `/hs game join`; `/hs game start` uses them as they were (documented in the wiki).
- **Windows can name a stale room** in their outline color and `/hs zones list` after rooms are redrawn. Gameplay uses the live room.
- **`/hs game status` prints raw internal text** for the phase (`RoundActive[round=3, toSpawn=12, spawnCooldown=5]`), and shows a downed player as "spectating".
- **Text still says "mystery box" or "box pool"**: four command titles in `BoxCommands`, "No box pool X is loaded" for map makers, "Unknown box pool X" shown to players, and the `[box]` toml comments.
- **Vending machine clicks and animation can repeat behind a lagging server.** Client visuals are timed from the client's game time, which the server snaps back once every 20 server ticks: a vend's key clicks can play twice and its use animation restart. The light fade, lit strip and hum jump only slightly, and the upgrader's fire only in its first half second. `feature/BoxSpin` is the fix to copy.
- **The minigun has a short supply.** A gun that feeds from the inventory fires straight from its dummy reserve and never reloads, so its 30-round magazine is never used and it gets `players.reserve_magazines` magazines (120 rounds by default) where other guns get one more.

## Datapacks and scripts

- **Scripts can escape the string sandbox.** TacZ bundles the same LuaJ and owns the JVM-wide string metatable, which `LuaRuntime` keeps. So `("x"):rep(n)` skips the `string.rep` length cap (`rep(2^31-1)` is one 2 GiB allocation, and running out of memory isn't caught), `string.dump` is reachable, and `getmetatable("").__index` lets a script change the string methods TacZ's own gun scripts use.
- **Out-of-range values still load.** Mob profiles, power-ups, wave events, crate pools and gun balance use `resultOrPartial`, which keeps an out-of-range value with only a log line: `"weight": -1` loads, `speed_multiplier: 0` makes zombies that never move, `damage_multiplier: 0` a gun that does no damage. A wrong type still skips the file. Upgrade files skip a bad entry.
- **A crawler tag without `speed_multiplier` freezes the zombie** (the missing value reads as 0), and the client draws it standing.
- **Script error line numbers point at the start of the function**: `InstructionBudget.onInstruction` doesn't call `super`, so the debug position never moves.
- **`/hs scripts reload` says "Scripts reloaded"** with `scripting.enabled = false`, and loads nothing; `/hs scripts check` ignores the setting.
- **A script command that fires an event at once refills the listener's instruction budget**, since every dispatch resets it (`LuaRuntime.guarded`). Not yet seen in a test.
- **Overriding the default crate pool with `{}` gives an empty pool**: the codec defaults `include_all_tacz_guns` to false; the built-in pool sets it true. Documented in the wiki, but easy to trip over.
- `game_start` fires at round 0, so a `round_min` of 1 or more never matches it (documented).

## Tests

- **The GameTest world isn't flat.** `run-gametest/server.properties` has `level-type=minecraft:normal`, so tests run at y=-60 among bedrock, deepslate and lava, on different terrain each run as batch order changes. Lava in arenas and caves cause flaky failures (the far-target hound, the wooden door, the outside-a-match terrain tests); a flat, fresh world removed them in trial runs.
- **The TacZ live-fire GameTests time with the wall clock** (`attachmentPropertyEventChangesRpmInLiveFire`), so a slow tick fails them. Backdating TacZ's own shot timestamps makes them independent of it.

## Not built, though `features/` says it is

- **Per-map setting overrides.** `SettingsResolver` exists and is tested, but `GameManager.settings()` always passes no override, and maps and map packs store none.
- **Validator checks** for the power switch being reachable without power (a switch behind a door that needs power can never be bought), the switch and upgrader being in or beside a room, and wall-buy guns still being installed (refused when set, but not after a gun pack is removed or a map imported). `features/map-validation.md` promises item problems as errors; they're warnings on purpose.
- `power.start_on`, powered machine blockstates, `wallbuys.default_pap_ammo_cost`, `windows.default_boards`, and `doors.max_members` aren't settings.

## Docs to tidy

- `features/mob-profiles-and-ai-injection.md` lost its first 88 lines (title, Goal, Data shape, Implementation, Config surface, Verification, Phase) in commit 3af5539; the last full copy is in f7aea67. What's left says the hellhound's `health_multiplier` is 0.33 (it's 0.0242424, 2 HP in round 5) and cites a GameTest that doesn't exist.
- Old setting names in `features/tacz-bridge.md` (`tacz.reserve_ammo_mags`, `tacz.pap_*`) and `features/sessions-and-multiplayer.md` (`sessions.*`). In the toml, the `base_count` comment says the whole count grows with the round (only the players' extra does), and the `reserve_magazines` comment says it's for starting guns (it's for every gun Hellstorm hands out).
- `features/tacz-bridge.md` and the wiki's Compatibility page say inventory-fed guns can't use dummy ammo; they can (see the minigun above).
- `features/wave-events.md` says the hound test checks 0.33 of a zombie's health; `features/mystery-box.md` says the crate can be used in the lobby (it can be seen, not used); `features/wall-buys.md` says there's no settings screen (there is); `features/power-switch.md` has no "As built" section and calls the switch's property `on` (it's the lever's `powered`).
- **Speeds are described wrong.** A mob's ground speed goes with the square of its speed attribute, so the crawl's 0.8 moves a crawler at about 0.64 of the speed, and the hellhound's `speed_multiplier` of 1.5 moves it about 2.25 times as fast. The feature docs, `Runners` and `Dismemberment` javadocs, and the wiki's Gameplay and Mob Profiles pages say 0.8 and 1.5 times.
