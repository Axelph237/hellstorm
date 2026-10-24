# Known issues

Found while writing the wiki for v0.0.1-prerelease, mostly by reading the code; few have been seen in game. None are fixed yet. Remove an item when it's fixed or decided against.

## Gameplay and commands

- **Trap times over 72000 ticks are lost.** `/hs trap active` and `/hs trap cooldown` accept any value, but the trap's codec caps both at 72000, so a larger value makes the trap's settings fail to save without a message, and it falls back to its defaults on reload.
- **Wall-buy refills charge for a full gun.** `TaczBridge.refillAmmo` always succeeds, so buying ammo for a gun that's already full costs points.
- **Inventory-fed guns get no ammo.** A gun that feeds from inventory items (the minigun) bought from a wall-buy or the crate has no usable reserve.
- **Room detection:** door blocks with no collision box (cobwebs) don't stop the fill; carpets, slabs, stairs and snow layers are left out of detected rooms; standing on a carpet gives "Your feet are inside a solid block".
- **Re-adding ignores arguments.** `/hs zones add window <id> <boards>` on an existing window keeps its old board count; `/hs zones add room <room> <start>` on an existing room ignores `start`; `/hs wallbuy set` resets the repurchase mode.
- **`/hs map import <name> replace` leaves the old map's blocks** (barricades, door blocks, machines) in the world.
- **`/hs game status` prints raw internal text** for the phase (`RoundActive[round=3, ...]`).
- **Validation runs when the lobby opens**, at the first `/hs game join`, not at `/hs game start`.
- **Windows can name a stale room** in their outline color and `/hs zones list`, after rooms are redrawn.
- **A round can stall** if a wave event's `mob_override` names only profiles that can't spawn, or every profile has weight 0.
- **Command titles say "mystery box"** in places (`Unable to set mystery box pool`).

## Datapacks and scripts

- **Out-of-range values still load.** Loaders use `resultOrPartial`, which keeps the bad value: `"weight": -1` is logged as an error and loads anyway.
- **A crawler tag without `speed_multiplier` freezes the zombie** (the missing value reads as 0).
- **Script error line numbers point at the start of the function**: `InstructionBudget.onInstruction` doesn't call `super`, so the debug position never moves.
- **`string.rep`'s length cap can likely be bypassed** through TacZ's restored string metatable.
- **`/hs scripts reload` says "Scripts reloaded"** with `scripting.enabled = false`, and loads nothing.
- **Overriding the default crate pool with `{}` gives an empty pool**: the codec defaults `include_all_tacz_guns` to false; the built-in pool sets it true.
- `game_start` fires at round 0, so no `round_min` filter matches it; `mob_override` picks evenly, ignoring weights.

## Not built, though `features/` says it is

- **Per-map setting overrides.** `SettingsResolver` exists and is tested, but `GameManager.settings()` always passes no override, and maps and map packs store none.
- **Validator checks** for the power switch and upgrader being reachable, and for wall-buy gun ids existing. Upgrade-definition problems are warnings, not errors.
- `power.start_on`, powered machine blockstates, `wallbuys.default_pap_ammo_cost`, `windows.default_boards`, and `doors.max_members` aren't settings.

## Docs to tidy

- Old setting names in `features/tacz-bridge.md` (`tacz.reserve_ammo_mags`, `tacz.pap_*`) and `features/sessions-and-multiplayer.md` (`sessions.*`); the `base_count` config comment; the speed description in `features/game-loop-and-rounds.md`.
- `features/mob-profiles-and-ai-injection.md` was cut to a few lines (commit 3af5539) and still says the hellhound's `health_multiplier` is 0.33; `features/wave-events.md` describes the hound test the old way; `features/mystery-box.md` says the crate works in the lobby (it doesn't); `features/wall-buys.md` says there's no settings screen (there is); `features/power-switch.md` has no "As built" section.
